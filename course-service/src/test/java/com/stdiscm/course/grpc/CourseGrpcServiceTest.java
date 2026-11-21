package com.stdiscm.course.grpc;

import com.stdiscm.course.model.Course;
import com.stdiscm.course.model.Enrollment;
import com.stdiscm.course.repository.CourseRepository;
import com.stdiscm.course.repository.EnrollmentRepository;
import com.stdiscm.proto.course.CourseServiceGrpc;
import com.stdiscm.proto.course.EnrollRequest;
import com.stdiscm.proto.course.EnrollResponse;
import com.stdiscm.proto.course.ListCoursesRequest;
import com.stdiscm.proto.course.ListCoursesResponse;
import com.stdiscm.proto.course.ListMyEnrollmentsRequest;
import com.stdiscm.proto.course.ListMyEnrollmentsResponse;
import com.stdiscm.proto.course.ListRosterRequest;
import com.stdiscm.proto.course.ListRosterResponse;
import com.stdiscm.sharedauth.AuthContext.Role;
import com.stdiscm.sharedauth.AuthServerInterceptor;
import com.stdiscm.sharedauth.JwtVerifier;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Drives the real gRPC service through an in-process server with the shared
 * authentication interceptor, so role checks and status codes are exercised
 * exactly as the gateway sees them. Repositories are mocked; the database
 * atomicity itself is covered by the SQL guard and the e2e concurrency test.
 */
class CourseGrpcServiceTest {

    private static final String STUDENT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String FACULTY_ID = "22222222-2222-2222-2222-222222222221";

    private KeyPair keys;
    private Server server;
    private ManagedChannel channel;
    private CourseRepository courses;
    private EnrollmentRepository enrollments;

    @BeforeEach
    void start() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();

        courses = mock(CourseRepository.class);
        enrollments = mock(EnrollmentRepository.class);

        String name = "course-test-" + UUID.randomUUID();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new CourseGrpcService(courses, enrollments))
                .intercept(new AuthServerInterceptor(new JwtVerifier(keys.getPublic()), Set.of()))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void studentEnrollReservesSeatThenInsertsEnrollment() {
        when(courses.reserveSeat("CS101")).thenReturn(1);
        Course updated = course("CS101", 25, 1, true);
        when(courses.findById("CS101")).thenReturn(Optional.of(updated));
        when(enrollments.saveAndFlush(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

        EnrollResponse response = stub(Role.STUDENT).enroll(enrollRequest("CS101"));

        assertEquals(1, response.getCourse().getEnrolled());
        ArgumentCaptor<Enrollment> saved = ArgumentCaptor.forClass(Enrollment.class);
        InOrder order = inOrder(courses, enrollments);
        order.verify(courses).reserveSeat("CS101");
        order.verify(enrollments).saveAndFlush(saved.capture());

        assertEquals(UUID.fromString(STUDENT_ID), saved.getValue().getStudentId());
        assertEquals("CS101", saved.getValue().getCourseCode());
        assertEquals("Alice Tan", saved.getValue().getStudentName());
    }

    @Test
    void fullCourseReturnsFailedPreconditionFull() {
        when(courses.reserveSeat("FULL101")).thenReturn(0);
        Course full = course("FULL101", 10, 10, true);
        when(courses.findById("FULL101")).thenReturn(Optional.of(full));

        Status status = statusOf(() -> stub(Role.STUDENT).enroll(enrollRequest("FULL101")));

        assertEquals(Status.Code.FAILED_PRECONDITION, status.getCode());
        assertEquals("FULL", status.getDescription());
        verify(enrollments, never()).saveAndFlush(any());
    }

    @Test
    void closedCourseReturnsFailedPreconditionClosed() {
        when(courses.reserveSeat("CLOSED01")).thenReturn(0);
        Course closed = course("CLOSED01", 20, 3, false);
        when(courses.findById("CLOSED01")).thenReturn(Optional.of(closed));

        Status status = statusOf(() -> stub(Role.STUDENT).enroll(enrollRequest("CLOSED01")));

        assertEquals(Status.Code.FAILED_PRECONDITION, status.getCode());
        assertEquals("CLOSED", status.getDescription());
        verify(enrollments, never()).saveAndFlush(any());
    }

    @Test
    void unknownCourseReturnsNotFound() {
        when(courses.reserveSeat("NOPE")).thenReturn(0);
        when(courses.findById("NOPE")).thenReturn(Optional.empty());

        Status status = statusOf(() -> stub(Role.STUDENT).enroll(enrollRequest("NOPE")));

        assertEquals(Status.Code.NOT_FOUND, status.getCode());
    }

    @Test
    void duplicateEnrollmentReturnsAlreadyExists() {
        when(courses.reserveSeat("CS101")).thenReturn(1);
        when(enrollments.saveAndFlush(any(Enrollment.class))).thenThrow(
                new DataIntegrityViolationException("duplicate key",
                        new SQLException("duplicate key", "23505")));

        Status status = statusOf(() -> stub(Role.STUDENT).enroll(enrollRequest("CS101")));

        assertEquals(Status.Code.ALREADY_EXISTS, status.getCode());
        assertEquals("ALREADY_ENROLLED", status.getDescription());
    }

    @Test
    void facultyCannotEnroll() {
        Status status = statusOf(() -> stub(Role.FACULTY).enroll(enrollRequest("CS101")));

        assertEquals(Status.Code.PERMISSION_DENIED, status.getCode());
        verifyNoInteractions(courses, enrollments);
    }

    @Test
    void studentCannotListRoster() {
        Status status = statusOf(() -> stub(Role.STUDENT).listRoster(rosterRequest("STDISCM")));

        assertEquals(Status.Code.PERMISSION_DENIED, status.getCode());
        verifyNoInteractions(courses, enrollments);
    }

    @Test
    void facultyListsRoster() {
        Course stdiscm = course("STDISCM", 30, 2, true);
        when(courses.findById("STDISCM")).thenReturn(Optional.of(stdiscm));
        when(enrollments.findByCourseCodeOrderByStudentNameAsc("STDISCM"))
                .thenReturn(List.of(new Enrollment(UUID.fromString(STUDENT_ID), "STDISCM", "Alice Tan")));

        ListRosterResponse response = stub(Role.FACULTY).listRoster(rosterRequest("STDISCM"));

        assertEquals("STDISCM", response.getCourseCode());
        assertEquals(1, response.getStudentsCount());
        assertEquals(STUDENT_ID, response.getStudents(0).getStudentId());
        assertEquals("Alice Tan", response.getStudents(0).getStudentName());
    }

    @Test
    void studentListsOwnEnrollments() {
        Course stdiscm = course("STDISCM", 30, 2, true);
        when(enrollments.findCoursesForStudent(UUID.fromString(STUDENT_ID)))
                .thenReturn(List.of(stdiscm));

        ListMyEnrollmentsResponse response = stub(Role.STUDENT)
                .listMyEnrollments(ListMyEnrollmentsRequest.getDefaultInstance());

        assertEquals(1, response.getCoursesCount());
        assertEquals("STDISCM", response.getCourses(0).getCode());
    }

    @Test
    void anyLoggedInUserListsCourses() {
        Course cs101 = course("CS101", 25, 0, true);
        Course full101 = course("FULL101", 10, 10, true);
        when(courses.findAllByOrderByCourseCodeAsc()).thenReturn(List.of(cs101, full101));

        ListCoursesResponse response = stub(Role.FACULTY)
                .listCourses(ListCoursesRequest.getDefaultInstance());

        assertEquals(2, response.getCoursesCount());
        assertEquals("CS101", response.getCourses(0).getCode());
    }

    @Test
    void missingTokenIsUnauthenticated() {
        Status status = statusOf(() -> CourseServiceGrpc.newBlockingStub(channel)
                .listCourses(ListCoursesRequest.getDefaultInstance()));

        assertEquals(Status.Code.UNAUTHENTICATED, status.getCode());
    }

    // ------------------------------------------------------------------ helpers

    private CourseServiceGrpc.CourseServiceBlockingStub stub(Role role) {
        CourseServiceGrpc.CourseServiceBlockingStub stub = CourseServiceGrpc.newBlockingStub(channel);
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer " + token(role));
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private String token(Role role) {
        long now = System.currentTimeMillis();
        boolean student = role == Role.STUDENT;
        return Jwts.builder()
                .id("jti-test")
                .subject(student ? STUDENT_ID : FACULTY_ID)
                .claim("role", role.name())
                .claim("name", student ? "Alice Tan" : "Prof. Reyes")
                .issuedAt(new Date(now - 60_000))
                .expiration(new Date(now + 60_000))
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static Course course(String code, int capacity, int enrolled, boolean open) {
        Course c = mock(Course.class);
        when(c.getCourseCode()).thenReturn(code);
        when(c.getCourseTitle()).thenReturn("Title " + code);
        when(c.getCourseCapacity()).thenReturn(capacity);
        when(c.getEnrolledCount()).thenReturn(enrolled);
        when(c.isOpen()).thenReturn(open);
        return c;
    }

    private static EnrollRequest enrollRequest(String code) {
        return EnrollRequest.newBuilder().setCourseCode(code).build();
    }

    private static ListRosterRequest rosterRequest(String code) {
        return ListRosterRequest.newBuilder().setCourseCode(code).build();
    }

    private static Status statusOf(Runnable call) {
        return assertThrows(StatusRuntimeException.class, call::run).getStatus();
    }
}

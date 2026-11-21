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
import com.stdiscm.proto.course.RosterEntry;
import com.stdiscm.sharedauth.AuthContext.Principal;
import com.stdiscm.sharedauth.AuthContext.Role;
import com.stdiscm.sharedauth.RoleGuard;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Course node gRPC methods. The caller's ID and role always come from the
 * verified JWT (shared-auth), never from the request.
 */
@GrpcService
public class CourseGrpcService extends CourseServiceGrpc.CourseServiceImplBase {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;

    public CourseGrpcService(CourseRepository courses, EnrollmentRepository enrollments) {
        this.courses = courses;
        this.enrollments = enrollments;
    }

    @Override
    public void listCourses(ListCoursesRequest request, StreamObserver<ListCoursesResponse> out) {
        RoleGuard.require(Role.STUDENT, Role.FACULTY);

        ListCoursesResponse.Builder response = ListCoursesResponse.newBuilder();
        courses.findAllByOrderByCourseCodeAsc().forEach(c -> response.addCourses(toProto(c)));
        out.onNext(response.build());
        out.onCompleted();
    }

    /**
     * One transaction:
     *   1. conditional UPDATE reserves a seat only if the course is open and not full;
     *   2. INSERT the enrollment; the unique constraint rejects a duplicate.
     *
     * Any failure throws a StatusRuntimeException, so Spring rolls the whole
     * transaction back (including the seat increment). This is what makes a
     * duplicate or failed insert safe under concurrency.
     */
    @Override
    @Transactional
    public void enroll(EnrollRequest request, StreamObserver<EnrollResponse> out) {
        Principal student = RoleGuard.require(Role.STUDENT);

        String code = request.getCourseCode().trim();
        if (code.isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("course_code is required").asRuntimeException();
        }

        int reserved = courses.reserveSeat(code);
        if (reserved == 0) {
            throw reasonSeatWasNotReserved(code);
        }

        try {
            enrollments.saveAndFlush(
                    new Enrollment(studentId(student), code, student.name()));
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw Status.ALREADY_EXISTS
                        .withDescription("ALREADY_ENROLLED")
                        .asRuntimeException();
            }
            throw Status.INTERNAL
                    .withDescription("enrollment could not be saved")
                    .withCause(e)
                    .asRuntimeException();
        }

        Course updated = courses.findById(code)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("course not found: " + code)
                        .asRuntimeException());

        out.onNext(EnrollResponse.newBuilder().setCourse(toProto(updated)).build());
        out.onCompleted();
    }

    @Override
    public void listMyEnrollments(ListMyEnrollmentsRequest request,
                                  StreamObserver<ListMyEnrollmentsResponse> out) {
        Principal student = RoleGuard.require(Role.STUDENT);

        ListMyEnrollmentsResponse.Builder response = ListMyEnrollmentsResponse.newBuilder();
        enrollments.findCoursesForStudent(studentId(student)).forEach(c -> response.addCourses(toProto(c)));
        out.onNext(response.build());
        out.onCompleted();
    }

    @Override
    public void listRoster(ListRosterRequest request, StreamObserver<ListRosterResponse> out) {
        RoleGuard.require(Role.FACULTY);

        String code = request.getCourseCode().trim();
        if (code.isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("course_code is required").asRuntimeException();
        }

        Course course = courses.findById(code)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("course not found: " + code)
                        .asRuntimeException());

        ListRosterResponse.Builder response = ListRosterResponse.newBuilder()
                .setCourseCode(course.getCourseCode());
        for (Enrollment e : enrollments.findByCourseCodeOrderByStudentNameAsc(code)) {
            response.addStudents(RosterEntry.newBuilder()
                    .setStudentId(e.getStudentId().toString())
                    .setStudentName(e.getStudentName())
                    .build());
        }

        out.onNext(response.build());
        out.onCompleted();
    }

    /** The UPDATE matched nothing: tell the caller whether it is missing, closed or full. */
    private RuntimeException reasonSeatWasNotReserved(String code) {
        Course course = courses.findById(code).orElse(null);
        if (course == null) {
            return Status.NOT_FOUND.withDescription("course not found: " + code).asRuntimeException();
        }
        if (!course.isOpen()) {
            return Status.FAILED_PRECONDITION.withDescription("CLOSED").asRuntimeException();
        }
        return Status.FAILED_PRECONDITION.withDescription("FULL").asRuntimeException();
    }

    private static UUID studentId(Principal principal) {
        try {
            return UUID.fromString(principal.userId());
        } catch (IllegalArgumentException e) {
            throw Status.UNAUTHENTICATED
                    .withDescription("token has an invalid user id")
                    .asRuntimeException();
        }
    }

    /** PostgreSQL SQLSTATE 23505 = unique_violation. */
    private static boolean isUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause instanceof SQLException sql && "23505".equals(sql.getSQLState());
    }

    private static com.stdiscm.proto.course.Course toProto(Course c) {
        return com.stdiscm.proto.course.Course.newBuilder()
                .setCode(c.getCourseCode())
                .setTitle(c.getCourseTitle())
                .setCapacity(c.getCourseCapacity())
                .setEnrolled(c.getEnrolledCount())
                .setOpen(c.isOpen())
                .build();
    }
}

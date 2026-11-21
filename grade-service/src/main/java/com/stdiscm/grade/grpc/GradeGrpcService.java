package com.stdiscm.grade.grpc;

import com.stdiscm.grade.model.FacultyCourse;
import com.stdiscm.grade.model.Grade;
import com.stdiscm.grade.service.GradeRules.RawEntry;
import com.stdiscm.grade.service.GradeService;
import com.stdiscm.proto.grade.GetMyGradesRequest;
import com.stdiscm.proto.grade.GetMyGradesResponse;
import com.stdiscm.proto.grade.GradeServiceGrpc;
import com.stdiscm.proto.grade.ListMyTeachingCoursesRequest;
import com.stdiscm.proto.grade.ListMyTeachingCoursesResponse;
import com.stdiscm.proto.grade.TeachingCourse;
import com.stdiscm.proto.grade.UploadGradesRequest;
import com.stdiscm.proto.grade.UploadGradesResponse;
import com.stdiscm.sharedauth.AuthContext.Principal;
import com.stdiscm.sharedauth.AuthContext.Role;
import com.stdiscm.sharedauth.RoleGuard;
import com.stdiscm.shareddb.DbRetry;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.UUID;

/**
 * gRPC adapter for the grade node (contract: proto/src/main/proto/grade.proto): checks the role,
 * turns protobuf into plain values, calls {@link GradeService}, turns the result back into protobuf.
 * Reads go through {@link DbRetry#read} (one retry if the primary died mid-query; the retry lands on
 * the replica). Uploads are not retried. Errors are thrown and mapped to statuses by the @GrpcAdvice
 * classes: GradeExceptionAdvice (bad input, not your course), DatabaseExceptionAdvice (database down),
 * StatusExceptionAdvice from shared-auth (UNAUTHENTICATED / PERMISSION_DENIED from RoleGuard).
 */
@GrpcService
public class GradeGrpcService extends GradeServiceGrpc.GradeServiceImplBase {

    private final GradeService grades;

    public GradeGrpcService(GradeService grades) {
        this.grades = grades;
    }

    @Override
    public void getMyGrades(GetMyGradesRequest request, StreamObserver<GetMyGradesResponse> out) {
        Principal student = RoleGuard.require(Role.STUDENT);
        UUID studentId = UUID.fromString(student.userId());

        GetMyGradesResponse.Builder response = GetMyGradesResponse.newBuilder();
        DbRetry.read(() -> grades.gradesOf(studentId)).forEach(g -> response.addGrades(toProto(g)));
        out.onNext(response.build());
        out.onCompleted();
    }

    @Override
    public void listMyTeachingCourses(ListMyTeachingCoursesRequest request,
                                      StreamObserver<ListMyTeachingCoursesResponse> out) {
        Principal faculty = RoleGuard.require(Role.FACULTY);
        UUID facultyId = UUID.fromString(faculty.userId());

        ListMyTeachingCoursesResponse.Builder response = ListMyTeachingCoursesResponse.newBuilder();
        for (FacultyCourse c : DbRetry.read(() -> grades.coursesTaughtBy(facultyId))) {
            response.addCourses(TeachingCourse.newBuilder()
                    .setCourseCode(c.getCourseCode())
                    .setCourseTitle(c.getCourseTitle()));
        }
        out.onNext(response.build());
        out.onCompleted();
    }

    @Override
    public void uploadGrades(UploadGradesRequest request, StreamObserver<UploadGradesResponse> out) {
        Principal faculty = RoleGuard.require(Role.FACULTY);

        List<RawEntry> entries = request.getGradesList().stream()
                .map(e -> new RawEntry(e.getStudentId(), e.getGrade()))
                .toList();
        int saved = grades.upload(UUID.fromString(faculty.userId()), faculty.name(),
                request.getCourseCode(), request.getTerm(), entries);

        out.onNext(UploadGradesResponse.newBuilder().setSaved(saved).build());
        out.onCompleted();
    }

    /** Entity -> protobuf. The protobuf message is also called Grade, hence the full name. */
    private static com.stdiscm.proto.grade.Grade toProto(Grade g) {
        return com.stdiscm.proto.grade.Grade.newBuilder()
                .setStudentId(g.getStudentId().toString())
                .setCourseCode(g.getCourseCode())
                .setCourseTitle(g.getCourseTitle())
                .setTerm(g.getTerm())
                .setGrade(g.getGrade().doubleValue())
                .setUploadedBy(g.getUploadedBy().toString())
                .build();
    }
}

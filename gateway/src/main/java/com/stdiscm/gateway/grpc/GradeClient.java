package com.stdiscm.gateway.grpc;

import com.stdiscm.proto.grade.GetMyGradesRequest;
import com.stdiscm.proto.grade.Grade;
import com.stdiscm.proto.grade.GradeServiceGrpc.GradeServiceBlockingStub;
import com.stdiscm.proto.grade.ListMyTeachingCoursesRequest;
import com.stdiscm.proto.grade.TeachingCourse;
import com.stdiscm.proto.grade.UploadGradesRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GradeClient {

    private final GradeServiceBlockingStub stub;
    private final ServiceChannel channel;

    public GradeClient(GradeServiceBlockingStub stub, @Qualifier("gradeChannel") ServiceChannel channel) {
        this.stub = stub;
        this.channel = channel;
    }

    public List<Grade> getMyGrades(String token) {
        return Calls.run(channel, () -> Calls.withToken(stub, token)
                .getMyGrades(GetMyGradesRequest.getDefaultInstance()).getGradesList());
    }

    public List<TeachingCourse> listMyTeachingCourses(String token) {
        return Calls.run(channel, () -> Calls.withToken(stub, token)
                .listMyTeachingCourses(ListMyTeachingCoursesRequest.getDefaultInstance()).getCoursesList());
    }

    public int uploadGrades(String token, UploadGradesRequest request) {
        return Calls.run(channel, () -> Calls.withToken(stub, token).uploadGrades(request).getSaved());
    }
}

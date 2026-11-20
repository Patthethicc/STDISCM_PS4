package com.stdiscm.gateway.grpc;

import com.stdiscm.proto.course.Course;
import com.stdiscm.proto.course.CourseServiceGrpc.CourseServiceBlockingStub;
import com.stdiscm.proto.course.EnrollRequest;
import com.stdiscm.proto.course.ListCoursesRequest;
import com.stdiscm.proto.course.ListMyEnrollmentsRequest;
import com.stdiscm.proto.course.ListRosterRequest;
import com.stdiscm.proto.course.ListRosterResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CourseClient {

    private final CourseServiceBlockingStub stub;
    private final ServiceChannel channel;

    public CourseClient(CourseServiceBlockingStub stub, @Qualifier("courseChannel") ServiceChannel channel) {
        this.stub = stub;
        this.channel = channel;
    }

    public List<Course> listCourses(String token) {
        return Calls.run(channel, () -> Calls.withToken(stub, token)
                .listCourses(ListCoursesRequest.getDefaultInstance()).getCoursesList());
    }

    public Course enroll(String token, String courseCode) {
        return Calls.run(channel, () -> Calls.withToken(stub, token)
                .enroll(EnrollRequest.newBuilder().setCourseCode(courseCode).build()).getCourse());
    }

    public List<Course> listMyEnrollments(String token) {
        return Calls.run(channel, () -> Calls.withToken(stub, token)
                .listMyEnrollments(ListMyEnrollmentsRequest.getDefaultInstance()).getCoursesList());
    }

    public ListRosterResponse listRoster(String token, String courseCode) {
        return Calls.run(channel, () -> Calls.withToken(stub, token)
                .listRoster(ListRosterRequest.newBuilder().setCourseCode(courseCode).build()));
    }
}

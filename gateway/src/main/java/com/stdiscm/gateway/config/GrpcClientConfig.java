package com.stdiscm.gateway.config;

import com.stdiscm.gateway.grpc.DeadlineInterceptor;
import com.stdiscm.gateway.grpc.ServiceChannel;
import com.stdiscm.proto.announcement.AnnouncementServiceGrpc;
import com.stdiscm.proto.auth.AuthServiceGrpc;
import com.stdiscm.proto.course.CourseServiceGrpc;
import com.stdiscm.proto.grade.GradeServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One gRPC channel per service node. Each node can go down independently;
 * a dead channel only affects the features of that node.
 */
@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ServiceChannel authChannel(GatewayProperties props) {
        return new ServiceChannel("auth", props.services().auth(), new DeadlineInterceptor(props.deadlineMs()));
    }

    @Bean(destroyMethod = "shutdown")
    public ServiceChannel courseChannel(GatewayProperties props) {
        return new ServiceChannel("course", props.services().course(), new DeadlineInterceptor(props.deadlineMs()));
    }

    @Bean(destroyMethod = "shutdown")
    public ServiceChannel gradeChannel(GatewayProperties props) {
        return new ServiceChannel("grade", props.services().grade(), new DeadlineInterceptor(props.deadlineMs()));
    }

    @Bean(destroyMethod = "shutdown")
    public ServiceChannel announcementChannel(GatewayProperties props) {
        return new ServiceChannel("announcement", props.services().announcement(), new DeadlineInterceptor(props.deadlineMs()));
    }

    @Bean
    public AuthServiceGrpc.AuthServiceBlockingStub authStub(ServiceChannel authChannel) {
        return AuthServiceGrpc.newBlockingStub(authChannel.channel());
    }

    @Bean
    public CourseServiceGrpc.CourseServiceBlockingStub courseStub(ServiceChannel courseChannel) {
        return CourseServiceGrpc.newBlockingStub(courseChannel.channel());
    }

    @Bean
    public GradeServiceGrpc.GradeServiceBlockingStub gradeStub(ServiceChannel gradeChannel) {
        return GradeServiceGrpc.newBlockingStub(gradeChannel.channel());
    }

    @Bean
    public AnnouncementServiceGrpc.AnnouncementServiceBlockingStub announcementStub(ServiceChannel announcementChannel) {
        return AnnouncementServiceGrpc.newBlockingStub(announcementChannel.channel());
    }
}

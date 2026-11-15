package com.stdiscm.feature.grpc;

import com.google.protobuf.Timestamp;
import com.stdiscm.feature.model.AnnouncementEntity;
import com.stdiscm.feature.repository.AnnouncementRepository;
import com.stdiscm.proto.announcement.Announcement;
import com.stdiscm.proto.announcement.AnnouncementServiceGrpc;
import com.stdiscm.proto.announcement.ListAnnouncementsRequest;
import com.stdiscm.proto.announcement.ListAnnouncementsResponse;
import com.stdiscm.proto.announcement.PostAnnouncementRequest;
import com.stdiscm.sharedauth.AuthContext.Principal;
import com.stdiscm.sharedauth.AuthContext.Role;
import com.stdiscm.sharedauth.RoleGuard;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Instant;
import java.util.UUID;

@GrpcService
public class AnnouncementGrpcService extends AnnouncementServiceGrpc.AnnouncementServiceImplBase {

    static final int MAX_TITLE = 200;
    static final int MAX_BODY = 5000;

    private final AnnouncementRepository repo;

    public AnnouncementGrpcService(AnnouncementRepository repo) {
        this.repo = repo;
    }

    @Override
    public void postAnnouncement(PostAnnouncementRequest request, StreamObserver<Announcement> out) {
        Principal faculty = RoleGuard.require(Role.FACULTY);

        String title = request.getTitle().trim();
        String body = request.getBody().trim();
        if (title.isEmpty() || body.isEmpty()) {
            out.onError(Status.INVALID_ARGUMENT.withDescription("title and body are required").asRuntimeException());
            return;
        }
        if (title.length() > MAX_TITLE || body.length() > MAX_BODY) {
            out.onError(Status.INVALID_ARGUMENT
                    .withDescription("title max " + MAX_TITLE + " chars, body max " + MAX_BODY + " chars")
                    .asRuntimeException());
            return;
        }

        AnnouncementEntity saved = repo.save(
                new AnnouncementEntity(title, body, UUID.fromString(faculty.userId()), faculty.name()));
        out.onNext(toProto(saved));
        out.onCompleted();
    }

    @Override
    public void listAnnouncements(ListAnnouncementsRequest request, StreamObserver<ListAnnouncementsResponse> out) {
        RoleGuard.require(Role.STUDENT, Role.FACULTY);

        ListAnnouncementsResponse.Builder response = ListAnnouncementsResponse.newBuilder();
        repo.findAllByOrderByCreatedAtDesc().forEach(a -> response.addAnnouncements(toProto(a)));
        out.onNext(response.build());
        out.onCompleted();
    }

    private static Announcement toProto(AnnouncementEntity a) {
        Instant t = a.getCreatedAt();
        return Announcement.newBuilder()
                .setId(a.getId().toString())
                .setTitle(a.getTitle())
                .setBody(a.getBody())
                .setAuthorId(a.getAuthorId().toString())
                .setAuthorName(a.getAuthorName())
                .setCreatedAt(Timestamp.newBuilder().setSeconds(t.getEpochSecond()).setNanos(t.getNano()))
                .build();
    }
}

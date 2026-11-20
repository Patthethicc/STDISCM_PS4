package com.stdiscm.gateway.grpc;

import com.stdiscm.proto.announcement.Announcement;
import com.stdiscm.proto.announcement.AnnouncementServiceGrpc.AnnouncementServiceBlockingStub;
import com.stdiscm.proto.announcement.ListAnnouncementsRequest;
import com.stdiscm.proto.announcement.PostAnnouncementRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnnouncementClient {

    private final AnnouncementServiceBlockingStub stub;
    private final ServiceChannel channel;

    public AnnouncementClient(AnnouncementServiceBlockingStub stub,
                              @Qualifier("announcementChannel") ServiceChannel channel) {
        this.stub = stub;
        this.channel = channel;
    }

    public List<Announcement> list(String token) {
        return Calls.run(channel, () -> Calls.withToken(stub, token)
                .listAnnouncements(ListAnnouncementsRequest.getDefaultInstance()).getAnnouncementsList());
    }

    public Announcement post(String token, String title, String body) {
        return Calls.run(channel, () -> Calls.withToken(stub, token)
                .postAnnouncement(PostAnnouncementRequest.newBuilder().setTitle(title).setBody(body).build()));
    }
}

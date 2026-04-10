package com.asmsi.admin_system.listener;

import com.asmsi.admin_system.tracker.OnlineUserTracker;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class WebSocketPresenceListener {

    private final OnlineUserTracker tracker;

    public WebSocketPresenceListener(OnlineUserTracker tracker) {
        this.tracker = tracker;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            tracker.add(user.getName());
        }
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            tracker.remove(user.getName());
        }
    }
}
// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.weblayer_private;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.os.Build;

import androidx.annotation.StringDef;

import org.chromium.components.browser_ui.notifications.channels.ChannelDefinitions;
import org.chromium.components.browser_ui.notifications.channels.ChannelDefinitions.PredefinedChannel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TargetApi(Build.VERSION_CODES.O)
class WebLayerNotificationChannels extends ChannelDefinitions {
    private static class LazyHolder {
        private static WebLayerNotificationChannels sInstance = new WebLayerNotificationChannels();
    }

    public static WebLayerNotificationChannels getInstance() {
        return LazyHolder.sInstance;
    }

    private WebLayerNotificationChannels() {}

    /**
     * To define a new channel, add the channel ID to this StringDef and add a new entry to
     * PredefinedChannels.MAP below with the appropriate channel parameters. To remove an existing
     * channel, remove the ID from this StringDef, remove its entry from Predefined Channels.MAP,
     * and add it to the return value of {@link #getLegacyChannelIds()}.
     */
    @StringDef({ChannelId.MEDIA})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ChannelId {
        String MEDIA = "org.chromium.weblayer.media";
    }

    @StringDef({ChannelGroupId.WEBLAYER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ChannelGroupId {
        String WEBLAYER = "org.chromium.weblayer";
    }

    // Map defined in static inner class so it's only initialized lazily.
    @TargetApi(Build.VERSION_CODES.N) // for NotificationManager.IMPORTANCE_* constants
    private static class PredefinedChannels {
        static final Map<String, PredefinedChannel> MAP;

        static {
            Map<String, PredefinedChannel> map = new HashMap<>();
            map.put(ChannelId.MEDIA,
                    new PredefinedChannel(ChannelId.MEDIA, R.string.notification_category_media,
                            NotificationManager.IMPORTANCE_LOW, ChannelGroupId.WEBLAYER));
            MAP = Collections.unmodifiableMap(map);
        }
    }

    // Map defined in static inner class so it's only initialized lazily.
    private static class PredefinedChannelGroups {
        static final Map<String, PredefinedChannelGroup> MAP;
        static {
            Map<String, PredefinedChannelGroup> map = new HashMap<>();
            map.put(ChannelGroupId.WEBLAYER,
                    new PredefinedChannelGroup(ChannelGroupId.WEBLAYER,
                            R.string.weblayer_notification_channel_group_name));
            MAP = Collections.unmodifiableMap(map);
        }
    }

    @Override
    public Set<String> getAllChannelGroupIds() {
        return PredefinedChannelGroups.MAP.keySet();
    }

    @Override
    public Set<String> getAllChannelIds() {
        return PredefinedChannels.MAP.keySet();
    }

    @Override
    public Set<String> getStartupChannelIds() {
        return Collections.emptySet();
    }

    @Override
    public List<String> getLegacyChannelIds() {
        return Collections.emptyList();
    }

    @Override
    public PredefinedChannelGroup getChannelGroup(@ChannelGroupId String groupId) {
        return PredefinedChannelGroups.MAP.get(groupId);
    }

    @Override
    public PredefinedChannel getChannelFromId(@ChannelId String channelId) {
        return PredefinedChannels.MAP.get(channelId);
    }
}

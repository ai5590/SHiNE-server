package server.logic.ws_protocol.JSON.handlers.subscriptions.entyties;

import server.logic.ws_protocol.JSON.entyties.Net_Response;

import java.util.List;

/**
 * Ответ GetSubscribedChannels.
 *
 * payload:
 * {
 *   "channels": [
 *     {
 *       "channelLogin": "dima",
 *       "channelBchName": "dima-001",
 *       "publicationsCount": 123,
 *       "lastPublicationTimestampSec": 1736371200,
 *       "lastTextPreview": "...."
 *     }
 *   ]
 * }
 */
public class Net_GetSubscribedChannels_Response extends Net_Response {

    private List<ChannelInfo> channels;

    public List<ChannelInfo> getChannels() { return channels; }
    public void setChannels(List<ChannelInfo> channels) { this.channels = channels; }

    public static class ChannelInfo {

        private String channelLogin;
        private String channelBchName;

        private Integer publicationsCount;

        /** Unix seconds времени ПУБЛИКАЦИИ (оригинального TEXT_NEW). Nullable, если публикаций нет. */
        private Long lastPublicationTimestampSec;

        /** Первые 50 символов актуального текста (edit или orig). Nullable, если публикаций нет. */
        private String lastTextPreview;

        public String getChannelLogin() { return channelLogin; }
        public void setChannelLogin(String channelLogin) { this.channelLogin = channelLogin; }

        public String getChannelBchName() { return channelBchName; }
        public void setChannelBchName(String channelBchName) { this.channelBchName = channelBchName; }

        public Integer getPublicationsCount() { return publicationsCount; }
        public void setPublicationsCount(Integer publicationsCount) { this.publicationsCount = publicationsCount; }

        public Long getLastPublicationTimestampSec() { return lastPublicationTimestampSec; }
        public void setLastPublicationTimestampSec(Long lastPublicationTimestampSec) { this.lastPublicationTimestampSec = lastPublicationTimestampSec; }

        public String getLastTextPreview() { return lastTextPreview; }
        public void setLastTextPreview(String lastTextPreview) { this.lastTextPreview = lastTextPreview; }
    }
}
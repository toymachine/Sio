(ns sio.handler
  (:import
    [org.jboss.netty.channel
     ChannelUpstreamHandler ChannelDownstreamHandler
     ChannelPipelineFactory Channels ChannelHandler
     MessageEvent WriteCompletionEvent ChannelStateEvent
     ExceptionEvent ChannelState]))

(defn simple-channel-handler [msg-handlers]
  (let [handle-up (fn [msg ctx event]
                    (if-let [msg-handler (msg msg-handlers)]
                      (msg-handler ctx event)
                      (.sendUpstream ctx event)))
        handle-down (fn [msg ctx event]
                      (if-let [msg-handler (msg msg-handlers)]
                        (msg-handler ctx event)
                        (.sendDownstream ctx event)))]
    (reify
      ChannelUpstreamHandler
      (handleUpstream [_ ctx event]
                      ;(println "ups" event)
                      (cond
                        (instance? MessageEvent event) (handle-up :message-received ctx event)
                        (instance? WriteCompletionEvent event) (handle-up :write-complete ctx event)
                        (instance? ExceptionEvent event) (handle-up :exception-caught ctx event)
                        (instance? ChannelStateEvent event)
                        (let [event_state (.getState event)
                              event_value (.getValue event)]
                          (cond
                            (= ChannelState/OPEN event_state) (if (true? event_value)
                                                                (handle-up :channel-open ctx event)
                                                                (handle-up :channel-close ctx event))
                            (= ChannelState/BOUND event_state) (if (nil? event_value)
                                                                 (handle-up :channel-unbound ctx event)
                                                                 (handle-up :channel-bound ctx event))
                            (= ChannelState/CONNECTED event_state) (if (nil? event_value)
                                                                     (handle-up :channel-disconnected ctx event)
                                                                     (handle-up :channel-connected ctx event))
                            :else (.sendUpstream ctx event)
                            ))
                        :else (.sendUpstream ctx event)))
      ChannelDownstreamHandler
      (handleDownstream [_ ctx event]
                        ;(println "downs" event)
                        (cond
                          (instance? MessageEvent event) (handle-down :write-requested ctx event)
                          (instance? ChannelStateEvent event)
                          (let [event_state (.getState event)
                                event_value (.getValue event)]
                            (cond
                              (= ChannelState/OPEN event_state) (when (true? event_value)
                                                                  (handle-down :close-requested ctx event))
                              (= ChannelState/BOUND event_state) (if (nil? event_value)
                                                                   (handle-down :unbind-requested ctx event)
                                                                   (handle-down :bind-requested ctx event))
                              (= ChannelState/CONNECTED event_state) (if (nil? event_value)
                                                                       (handle-down :disconnect-requested ctx event)
                                                                       (handle-down :connect-requested ctx event))
                              :else (.sendDownstream ctx event)
                              ))
                          :else (.sendDownstream ctx event)
                          )))))

package com.cheeseocean.im.msg.service;

import com.cheeseocean.im.msg.api.ChatService;
import com.cheeseocean.im.msg.api.SendMsgReq;
import com.cheeseocean.im.msg.api.SendMsgResp;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * @author xxxcrel
 * @date 2023/4/17 14:17
 */
@Service
@DubboService
public class KafkaChatService implements ChatService {


    /**
     * 采用Kafka负责将消息落盘，
     */
    private KafkaProducer<String, byte[]> msgProducer;

//    @Qualifier("msgProducer")
//    @Autowired
//    public void setMsgProducer(KafkaProducer<String, byte[]> msgProducer) {
//        this.msgProducer = msgProducer;
//    }

    @Override
    public SendMsgResp sendMessage(SendMsgReq sendMsgReq) {
        SendMsgResp resp = new SendMsgResp();
        switch (sendMsgReq.getType()) {
            case 0:
                msgProducer.send(new ProducerRecord<>(sendMsgReq.getReceiverID(), sendMsgReq.getContent()));
            case 1:
            default:
                return resp;
        }
    }
}

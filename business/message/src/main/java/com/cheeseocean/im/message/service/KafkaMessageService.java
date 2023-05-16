package com.cheeseocean.im.message.service;

import com.alibaba.com.caucho.hessian.io.Hessian2Input;
import com.cheeseocean.im.common.codec.Hessian2ObjectInput;
import com.cheeseocean.im.common.entity.CheeseMessage;
import com.cheeseocean.im.message.api.MessageService;
import com.cheeseocean.im.message.api.SendMessageReq;
import com.cheeseocean.im.message.api.SendMessageResp;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * @author xxxcrel
 * @date 2023/4/17 14:17
 */
@Service
@DubboService
public class KafkaMessageService implements MessageService {


    /**
     * 采用Kafka负责将消息落盘，
     */
    private KafkaProducer<String, byte[]> msgProducer;


    @Qualifier("msgProducer")
    @Autowired
    public void setMsgProducer(KafkaProducer<String, byte[]> msgProducer) {
        this.msgProducer = msgProducer;
    }

    @Override
    public SendMessageResp sendMessage(SendMessageReq sendMessageReq) {
        SendMessageResp resp = new SendMessageResp();
        Hessian2ObjectInput hs2In = new Hessian2ObjectInput(new ByteArrayInputStream(sendMessageReq.getPayload()));
        CheeseMessage payload = null;
        try {
            payload = hs2In.readObject(CheeseMessage.class);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        switch (payload.getType()) {
            case 0:
                msgProducer.send(new ProducerRecord<>(payload.getRid(), payload.getContent()));
            case 1:
            default:
                return resp;
        }
    }
}

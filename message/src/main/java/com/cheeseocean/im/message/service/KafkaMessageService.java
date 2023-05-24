package com.cheeseocean.im.message.service;

import com.cheeseocean.im.common.codec.Hessian2ObjectInput;
import com.cheeseocean.im.common.entity.CheeseMessage;
import com.cheeseocean.im.message.api.MessageService;
import com.cheeseocean.im.message.api.SendMessageReq;
import com.cheeseocean.im.message.api.SendMessageResp;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class KafkaMessageService implements MessageService {

    /**
     * 采用Kafka负责将消息落盘，
     */
    @Autowired
    private KafkaProducer<String, byte[]> messageProducer;


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
        log.info("start send message to broker, msg:{}", payload);
        switch (payload.getType()) {
            case 0:
                messageProducer.send(new ProducerRecord<>(payload.getRid(), payload.getContent()));
            case 1:
            default:
                return resp;
        }
    }
}

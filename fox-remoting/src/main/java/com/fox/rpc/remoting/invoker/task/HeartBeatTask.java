package com.fox.rpc.remoting.invoker.task;

import com.fox.rpc.common.bean.InvokeRequest;
import com.fox.rpc.common.bean.InvokeResponse;
import com.fox.rpc.remoting.exception.HeartBeatExcepion;
import com.fox.rpc.remoting.invoker.ClientManager;
import com.fox.rpc.remoting.invoker.RemoteServiceCall;
import com.fox.rpc.remoting.invoker.api.Client;
import com.fox.rpc.remoting.invoker.async.CallbackFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by shenwenbo on 2017/3/25.
 */
public class HeartBeatTask implements Runnable {

    private static Logger LOGGER = LoggerFactory.getLogger(HeartBeatTask.class);

    public static final String HEART_TASK_SERVICE = "HeartbeatService/";

    public static final String HEART_TASK_METHOD = "heartbeat";

    public static int SERVICE_HEALTH_COUNT = 5;

    //递增心跳验证码
    private static AtomicLong heartBeatSeq = new AtomicLong();
    private static ConcurrentHashMap<String, HeartBeatStat> heartBeatStatConcurrentHashMap = new ConcurrentHashMap<>();
    //心跳时间间隔
    private static long INTERVAL = 5000;

    //    //重启心跳线程时间
//    private static long REFRESH_INTERVAL=5000;
    public HeartBeatTask() {

    }

    @Override
    public void run() {
        long sleep = INTERVAL;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(sleep);
                System.err.println("心跳线程运行中");
                long now = System.currentTimeMillis();
                //获取当前所有client
                List<Client> clients = ClientManager.getInstance().getClients();
                for (Client client : clients) {
                    if (client != null) {
                        sendHeartBeatRequest(client);
                    }
                }
                sleep = INTERVAL - (System.currentTimeMillis() - now);
                System.out.println("时间间隔" + sleep);
            } catch (InterruptedException e) {
                LOGGER.info("heartbeat task fail:", e);
            }
        }
    }

    public void sendHeartBeatRequest(Client client) {
        HeartBeatStat heartBeatStat = getHeartBeatStat(client.getAdress());
        InvokeRequest request = createHeartBeatRequest();
        try {
            InvokeResponse response = null;
            CallbackFuture callback = new CallbackFuture();
            response = RemoteServiceCall.requestInvoke(request, callback, client);
            if (response == null) {
                response = callback.get();
            }
            if (response == null) {
                throw new HeartBeatExcepion("response is null");
            } else if (response.hasException()) {
                throw response.getException();
            }
            processResponse(request, response, client);
        } catch (Exception e) {
            System.out.println(e.toString());
            LOGGER.info("heart beat request fail");
            heartBeatStat.incrFailed();
            serviceChangeNoify(client);
        } catch (Throwable throwable) {
            System.out.println(throwable.toString());
            LOGGER.info("heart beat request fail");
            heartBeatStat.incrFailed();
            serviceChangeNoify(client);
        }
    }

    public void processResponse(InvokeRequest request, InvokeResponse response, Client client) {
        if (request.getSeq() == response.getSeq()) {
            HeartBeatStat heartBeatStat = getHeartBeatStat(client.getAdress());
            heartBeatStat.incrSucceed();
            serviceChangeNoify(client);
            LOGGER.info("heart beat success");
        } else {
            LOGGER.info("client sel is not consta");
        }
    }

    public InvokeRequest createHeartBeatRequest() {
        InvokeRequest invokeRequest = new InvokeRequest();
        invokeRequest.setMessageType(com.fox.rpc.common.common.Constants.MESSAGE_TYPE_HEART);
        invokeRequest.setInterfaceName(null);
        invokeRequest.setMethodName(HEART_TASK_METHOD);
        invokeRequest.setSerialize(com.fox.rpc.common.common.Constants.HESSIAN_SERIALIEE);
        invokeRequest.setSeq(heartBeatSeq.getAndIncrement());
        invokeRequest.setRequestId(UUID.randomUUID().toString());
        invokeRequest.setCreateMillisTime(System.currentTimeMillis());
        return invokeRequest;
    }

    public HeartBeatStat getHeartBeatStat(String adress) {
        HeartBeatStat heartBeatStat = heartBeatStatConcurrentHashMap.get(adress);
        if (heartBeatStat == null) {

            heartBeatStat = new HeartBeatStat(adress);
            heartBeatStatConcurrentHashMap.putIfAbsent(adress, heartBeatStat);
        }
        return heartBeatStat;
    }


    public void serviceChangeNoify(Client client) {
        HeartBeatStat heartBeatStat = getHeartBeatStat(client.getAdress());
        if (heartBeatStat.succeedCounter.longValue() >= SERVICE_HEALTH_COUNT) {
            LOGGER.info("this servicce[" + client.getAdress() + "] is health:", client.getAdress());
        } else if (heartBeatStat.failedCounter.longValue() >= SERVICE_HEALTH_COUNT) {
            //服务不可用，摘除当前服务
            LOGGER.info("this servicce[" + client.getAdress() + "]  has been removed:", client.getAdress());
            heartBeatStat.resetCounter();
        }
    }

    class HeartBeatStat {
        String address;
        AtomicLong succeedCounter = new AtomicLong();
        AtomicLong failedCounter = new AtomicLong();

        public HeartBeatStat(String address) {
            this.address = address;
        }

        public void incrSucceed() {
            failedCounter.set(0L);
            succeedCounter.incrementAndGet();
        }

        public void incrFailed() {
            succeedCounter.set(0L);
            failedCounter.incrementAndGet();
        }

        public void resetCounter() {
            succeedCounter.set(0L);
            failedCounter.set(0L);
        }
    }


    public static void main(String[] args) {
        InvokeRequest invokeRequest = new InvokeRequest();
        invokeRequest.setMessageType(com.fox.rpc.common.common.Constants.MESSAGE_TYPE_HEART);
        invokeRequest.setInterfaceName(null);
        invokeRequest.setMethodName(HEART_TASK_METHOD);
        invokeRequest.setSerialize(com.fox.rpc.common.common.Constants.HESSIAN_SERIALIEE);
    }


}
package com.hs.administrator.test.utils;

import com.hs.hstechsdklibrary.commonutil.LogUtil;

import org.greenrobot.eventbus.EventBus;

/**
 * @auther : lwb
 * @time : 2018/4/24 11:47
 * @describe : 函数名：EventBus帮助类
 */
public class EventBusUtils {

    private static final String TAG = "EventBusUtils";
    private EventBusUtils() {
    }

    /**
     * 注册EventBus
     *
     * @param subscriber 订阅者对象
     */
    public static void register(Object subscriber) {
        if (!EventBus.getDefault().isRegistered(subscriber)) {
            LogUtil.e(TAG, "register: 注册成功");
            EventBus.getDefault().register(subscriber);
        } else {
            LogUtil.e(TAG, "register: 注册失败");
        }
    }

    /**
     * 取消注册EventBus
     *
     * @param subscriber 订阅者对象
     */
    public static void unregister(Object subscriber) {
        EventBus.getDefault().unregister(subscriber);
    }

    /**
     * 发布订阅事件
     *
     * @param event 事件对象
     */
    public static void post(Object event) {
        EventBus.getDefault().post(event);
    }

    /**
     * 发布粘性订阅事件
     *
     * @param event 事件对象
     */
    public static void postSticky(Object event) {
        EventBus.getDefault().postSticky(event);
    }

    /**
     * 移除指定的粘性订阅事件
     *
     * @param eventType class的字节码，例如：String.class
     */
    public static <T> void removeStickyEvent(Class<T> eventType) {
        T stickyEvent = EventBus.getDefault().getStickyEvent(eventType);
        if (stickyEvent != null) {
            EventBus.getDefault().removeStickyEvent((T) stickyEvent);
        }
    }

    /**
     * 移除所有的粘性订阅事件
     */
    public static void removeAllStickyEvents() {
        EventBus.getDefault().removeAllStickyEvents();
    }

    /**
     * 取消事件传送
     *
     * @param event 事件对象
     */
    public static void cancelEventDelivery(Object event) {
        EventBus.getDefault().cancelEventDelivery(event);
    }
}

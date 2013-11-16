package com.kongrui;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created with IntelliJ IDEA.
 * User: pc
 * Date: 13-11-4
 * Time: 下午11:09
 * To change this template use File | Settings | File Templates.
 */
public class MainClass {
    public static void main (String [] arg){
        new ClassPathXmlApplicationContext("applicationContext-main.xml");
    }
}
package com.ccb.demo.mvc.action;

import com.ccb.mvcframework.annotation.NAutowired;
import com.ccb.mvcframework.annotation.NController;
import com.ccb.mvcframework.annotation.NRequestMapping;
import com.ccb.mvcframework.annotation.NRequestParam;
import com.ccb.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@NController
@NRequestMapping("/demo")
public class DemoAction {
    @NAutowired
    private IDemoService demoService;

    @NRequestMapping("/query.json")
    public void query(HttpServletRequest request, HttpServletResponse response, @NRequestParam("name") String name){
        String result = demoService.get(name);
        try{
            response.getWriter().write(result);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    @NRequestMapping("/add.json")
    public void add(HttpServletRequest request, HttpServletResponse response, @NRequestParam("a") Integer a, @NRequestParam("b") Integer b){
        try{
            response.getWriter().write(a + "+" + b + "=" + (a + b));
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    @NRequestMapping("/remove.json")
    public void remove(HttpServletRequest request, HttpServletResponse response, @NRequestParam("id") Integer id){

    }
}

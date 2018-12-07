package com.ccb.mvcframework.servlet;

import com.ccb.mvcframework.annotation.NAutowired;
import com.ccb.mvcframework.annotation.NController;
import com.ccb.mvcframework.annotation.NRequestMapping;
import com.ccb.mvcframework.annotation.NService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * 启动入口
 */
public class NDispatchServlet extends HttpServlet {

    private static final long serialVersionUID = 1l;

    private static final String LOCATION = "contextConfigLocation";

    /**
     * 保存所有的配置信息
     */
    private Properties p = new Properties();

    /**
     * 保存所有被扫描到的相关的类名
     */
    private List<String> classNames = new ArrayList<String>();

    /**
     * 核心IOC容器，保存所有初始化的BEAN
     */
    private Map<String,Object> ioc = new HashMap<String, Object>();

    /**
     * 保存所有的URL和方法的映射关系
     */
    private Map<String, Method> handlerMapping = new HashMap<String, Method>();

    public NDispatchServlet(){
        super();
    }

    /**
     * 初始化，加载配置文件
      * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("NDispatchServlet init start...");
        //1.加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        //2.扫描所有相关的类
        doScanner(p.getProperty("scanPackage"));

        //3.初始化所有相关类的实例，并保存到IOC容器中
        doInstance();

        //4.依赖注入
        doAutowired();

        //5.构造HandlerMapping
        initHandlerMapping();

        System.out.println("NDispatchServlet init end...");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    /**
     * 执行业务处理
     * @param req
     * @param resp
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try{
            //开始匹配到对应的方法
            doDispatch(req,resp);
        }catch (Exception e){
            //如果匹配过程出现异常，将异常信息打印出去
            resp.getWriter().write("500 Exception,Details:" + Arrays.toString(e.getStackTrace()).replaceAll("\\[|\\","").replaceAll("\\s","\r\n"));
        }
    }

    private void doLoadConfig(String location){
        InputStream fis = null;

        fis = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            p.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try{
                if (null != fis){
                    fis.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void doScanner(String packageName){
        //将所有的包路径转换为文件路径
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());

        for (File file: dir.listFiles()){
            if(file.isDirectory()){
                doScanner(packageName + "." + file.getName());
            }else{
                classNames.add(packageName + "." + file.getName().replace(".class","").trim());
            }
        }
    }

    /**
     * 首字母小写
     * @param str
     * @return
     */
    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }


    private void doInstance(){
        if (classNames.size() == 0)
            return;

        try{
            for (String className: classNames){
                Class<?> clazz = Class.forName(className);

                if (clazz.isAnnotationPresent(NController.class)){
                    //默认将首字母小写为beanname
                    String beanname = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanname,clazz.newInstance());
                }else if(clazz.isAnnotationPresent(NService.class)){
                    NService service = clazz.getAnnotation(NService.class);
                    String beanname = service.value();

                    //如果用户设置了名字,就用用户自己设置
                    if(!"".equals(beanname.trim())){
                        ioc.put(beanname, clazz.newInstance());
                        continue;
                    }

                    //如果自己没设置，就按接口类型创建一个实例
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i: interfaces){
                        ioc.put(i.getName(),clazz.newInstance());
                    }
                }else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void doAutowired(){
        if (ioc.isEmpty())
            return;

        for (Map.Entry<String,Object> entry: ioc.entrySet()){

            //拿到实例对象中的所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field: fields){
                if (!field.isAnnotationPresent(NAutowired.class)){
                    continue;
                }

                NAutowired autowired = field.getAnnotation(NAutowired.class);

                String beanname = autowired.value().trim();

                if ("".equals(beanname)){
                    beanname = field.getType().getName();
                }

                field.setAccessible(true);

                try{
                    field.set(entry.getValue(),ioc.get(beanname));
                }catch (Exception e){
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void initHandlerMapping(){
        if (ioc.isEmpty()) return;

        for (Map.Entry<String,Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(NController.class)){
                continue;
            }

            String baseUrl = "";

            if(clazz.isAnnotationPresent(NRequestMapping.class)){
                NRequestMapping requestMapping = clazz.getAnnotation(NRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //获取Method的url配置
            Method[] methods = clazz.getMethods();
            for (Method method:methods){
                //没有加ReuqstMapping注解的直接忽略
                if (!method.isAnnotationPresent(NRequestMapping.class)){
                    continue;
                }

                //映射URL
                NRequestMapping requestMapping = method.getAnnotation(NRequestMapping.class);
                String url = "/" + baseUrl + "/" + requestMapping.value().replaceAll("/+", "/");
                handlerMapping.put(url,method);
                System.out.println("mapped " + url + "," + method);
            }
        }
    }

    private void doDispatch(HttpServletRequest request,HttpServletResponse response) throws Exception{
        if (this.handlerMapping.isEmpty()){
            return;
        }

        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");

        if (!this.handlerMapping.containsKey(url)){
            response.getWriter().write("404 Not Found");
            return;
        }

        Map<String,String[]> params = request.getParameterMap();
        Method method = this.handlerMapping.get(url);

        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //获取请求的参数
        Map<String,String[]> parameterMap = request.getParameterMap();
        //保存参数值
        Object[] paramValues = new Object[parameterTypes.length];

        //方法的参数列表
        for (int i =0; i<parameterTypes.length;i++){
            //根据参数名称，做某些处理
            Class parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class){
                //参数类型已明确，这边强转类型
                paramValues[i]= request;
                continue;
            }else if (parameterType == HttpServletResponse.class){
                paramValues[i] = response;
                continue;
            }else if (parameterType == String.class){
                for (Map.Entry<String,String[]> param: parameterMap.entrySet()){
                    String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\","").replaceAll(",\\s",",");
                    paramValues[i] = value;
                }
            }
        }

        try{
            String beanname = lowerFirstCase(method.getDeclaringClass().getSimpleName());
            //利用反射机制调用
            method.invoke(this.ioc.get(beanname),paramValues);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}

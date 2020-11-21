package com.bjtu.redis;

import com.alibaba.fastjson.JSON;
import redis.clients.jedis.Jedis;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.io.FileDeleteStrategy;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.io.monitor.FileEntry;

public class MyJedis {
    private Jedis jedis;
    private String[] JsonContent;
    private User[] Users;
    private String ReadJson(String fileName){//读取Json文件
        String jsonString = "";
        try {
            File jsonFile = new File(fileName);
            FileReader fileReader = new FileReader(jsonFile);
            Reader reader = new InputStreamReader(new FileInputStream(jsonFile),"utf-8");
            int ch = 0;
            StringBuffer sb = new StringBuffer();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            reader.close();
            fileReader.close();
            jsonString = sb.toString();
            return jsonString;
        } catch (IOException e) {
            System.out.println("读取Json文件出错");
            e.printStackTrace();
            return null;
        }
    }
    // 把json格式的字符串写到文件
    private boolean WriteJson(String filePath, String Content) {//往json里写文件
        FileWriter fw;
        try {
            fw = new FileWriter(filePath);
            PrintWriter out = new PrintWriter(fw);
            out.write(Content);
            out.println();
            fw.close();
            out.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static ArrayList<String> readfile(String filepath) {//根据文件夹名读取文件
        ArrayList<String> output=new ArrayList<String>();
        File file = new File(filepath);
        String[] filelist = file.list();
        for (int i = 0; i < filelist.length; i++) {
            File readfile = new File(filepath + "\\" + filelist[i]);
            if (!readfile.isDirectory()) {
                String FileName=readfile.getName();
                if(Pattern.matches(".+json$",FileName)){
                    output.add(readfile.getPath());
                }
            }
        }
        return output;
    }

    public MyJedis(ArrayList<String> JsonFileName) {//构造器
        jedis = JedisInstance.getInstance().getResource();//获得资源线程池
        JsonContent=new String[JsonFileName.size()];//Json内容
        Users=new User[JsonFileName.size()];//用户
        for (int i = 0; i < JsonFileName.size(); i++) {
            JsonContent[i]=ReadJson(JsonFileName.get(i));
            Users[i]=new User(JsonContent[i]);
            Users[i].setFileName(JsonFileName.get(i));
            System.out.println(Users[i]);
            //jedis.set(Users[i].getNo()+"Name",Users[i].getName());
        }
    }

    public void setCount(String key){//查询时加一
        int IntNo=Integer.parseInt(key);
        if(jedis.get(key)==null){
            jedis.set(key,"1");
        }
        else {
            jedis.incr(key);
            Users[IntNo].setAction();
            Users[IntNo].setCount(jedis.get(key));
            String jsonOutput= JSON.toJSONString(Users[IntNo]);//json序列化
            WriteJson("src/main/resources/"+key+".json",jsonOutput);
        }
        //依次增加查询次数
        jedis.sadd("MySet",Users[IntNo].getAction());
        jedis.zadd("MyZset",Integer.parseInt(Users[IntNo].getAction().substring(0,2)),Users[IntNo].getAction());//按照小时的顺序来排序
        jedis.lpush("MyList", Users[IntNo].getAction());
    }


    public void setCount(String key,int CountNumber){//多次查询
        int IntNo=Integer.parseInt(key);
        if(jedis.get(key)==null){
            jedis.set(key,"1");
        }
        else {
            for(int i=0;i<CountNumber;i++){//增加好几次
                jedis.incr(key);
            }
            Users[IntNo].setAction();
            Users[IntNo].setCount(jedis.get(key));
            String jsonOutput= JSON.toJSONString(Users[IntNo]);//json序列化
            WriteJson("src/main/resources/"+key+".json",jsonOutput);
        }
        //依次增加查询次数
        jedis.sadd("MySet",Users[IntNo].getAction());
        jedis.zadd("MyZset",Integer.parseInt(Users[IntNo].getAction().substring(0,2)),Users[IntNo].getAction());//按照小时的顺序来排序
        jedis.lpush("MyList", Users[IntNo].getAction());
    }

    public String showCount(String key){//获得查询次数
        if(jedis.get(key)==null){
            return "ERROR";
        }
        else {
            return jedis.get(key);
        }
    }

    public List<String> showList(int Number){//展示列表里的内容
        List<String> list = jedis.lrange("MyList",0,Number);
        for(int i=0; i<list.size(); i++) {
            System.out.println("列表项为: "+list.get(i));
        }
        return list;
    }

    public List<String> showGiventime(int begin,int end){//不懂这个是不是滑动窗口的意思
        if (end<begin){//保证end比begin大
            int temp=begin;
            begin=end;
            end=temp;
        }
        List<String> list = jedis.lrange("MyList",begin,end);
        //List<String> result = null ;
        for(int i=0; i<list.size(); i++) {
            String temp=list.get(i);
            if(Integer.parseInt(temp.substring(2))<=end&&Integer.parseInt(temp.substring(2))>=begin) {
                list.add(temp);
            }
        }
        return list;
    }

    public Set<String> showSet(){//展示set
        Set<String> set = jedis.smembers("MySet");
        System.out.println(set);
        return set;
    }

    public Set<String> showZset(){//把小时设为权重标准进行排序
        Set<String> set = jedis.zrangeByScore("MyZset",0,24);
        System.out.println(set);
        return set;
    }

}

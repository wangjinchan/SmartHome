package com.example.smarthome;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
/**
 * 建立socket连接实现tcp/ip协议的方式：
 *1.创建Socket（安卓作为客户端，所以是client，单片机作为server端）
 *
 *2.打开连接到Socket的输入/输出流
 *
 *3.按照协议对Socket进行读/写操作
 *
 *4.关闭输入输出流、关闭Socket

 */
public class MainActivity extends AppCompatActivity {
    private Button button;
    private TextView textView;
    private Button startButton;//连接按钮
    private EditText IPText;//ip地址输入
    private boolean isConnecting=false;//判断是否连接
    private Thread mThreadClient=null;//子线程
    private Socket mSocketClient=null;//socket实现tcp、ip协议，实现tcp server和tcp client的连接
    private static PrintWriter mPrintWriterClient=null;//PrintWriter是java中很常见的一个类，该类可用来创建一个文件并向文本文件写入数据
    private  String res="";//接收的数据
    private  TextView warning_show, temp, mq;//警告语  温湿度  气体浓度
    private String []send_order={"1\n","2\n","3\n","4\n"};//发送的指令 1开启通风  2 关闭通风 3开启抽湿 4关闭抽湿

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);//该activity绑定的xml界面是activity_main.xml
        setContentView(R.layout.activity_main);//该activity绑定的xml界面是activity_main.xml
        strictMode();//严苛模式
        initView();//初始化显示的功能组件
    }

    /**
     * 严苛模式
     * StrictMode类是Android 2.3 （API 9）引入的一个工具类，可以用来帮助开发者发现代码中的一些不规范的问题，
     * 以达到提升应用响应能力的目的。举个例子来说，如果开发者在UI线程中进行了网络操作或者文件系统的操作，
     * 而这些缓慢的操作会严重影响应用的响应能力，甚至出现ANR对话框。为了在开发中发现这些容易忽略的问题，
     * 我们使用StrictMode，系统检测出主线程违例的情况并做出相应的反应，最终帮助开发者优化和改善代码逻辑。
     */
    private void strictMode(){
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        );
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .penaltyDeath()
                .build());
    }

    /**
     * layout组件初始化
     */
    private void initView(){
        warning_show = findViewById(R.id.tv1);//警告语显示
        temp = findViewById(R.id.temp_text);//温湿度显示
        mq = findViewById(R.id.mq_text);//气体浓度显示

        IPText= findViewById(R.id.IPText);//ip地址和端口号
        final String IP_PORT = "192.168.1.120:8080";
        IPText.setText(IP_PORT);//把ip地址和端口号设一个默认值，这个要改成你自己设置的

        textView=findViewById(R.id.test);
        button=findViewById(R.id.button_test);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                button.setText("真的变了耶");

            }
        });

        startButton= findViewById(R.id.StartConnect);//连接按钮
        //连接事件  其实就是建立socket连接

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isConnecting)
                {
                    isConnecting=false;
                    if(mSocketClient!=null)
                    {
                        try{
                            mSocketClient.close();
                            mSocketClient = null;
                            if (mPrintWriterClient!=null){
                                mPrintWriterClient.close();
                                mPrintWriterClient = null;
                            }
                            mThreadClient.interrupt();
                            startButton.setText("开始连接");
                            IPText.setEnabled(true);//可以输入ip和端口号
                            warning_show.setText("断开连接\n");

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }else
                {
                    mThreadClient = new Thread(mRunnable);
                    mThreadClient.start();
                }
            }
        });

        //通风开关按钮初始化
        final Switch switch_c=findViewById(R.id.switch_c);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            switch_c.setShowText(true);//按钮上默认显示文字
        }
        switch_c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)//当isChecked为true时，按钮就打开，并发送开的指令，当isChecked为false时，按钮就关闭，并发送关的指令
                {
                    switch_c.setSwitchTextAppearance(MainActivity.this,R.style.s_true);//开关样式
                    switch_c.setShowText(true);//显示开关为on
                    if (send(send_order[0],-1)){
                        showDialog("开启通风");
                    }else{
                        switch_c.setChecked(false);//当APP没有连接到单片机时，默认此按钮点击无效
                    }
                }else{

                    switch_c.setSwitchTextAppearance(MainActivity.this,R.style.s_false);//开关样式
                    switch_c.setShowText(true);//显示文字on
                    if (send(send_order[1],-1)){
                        showDialog("关闭通风");
                    }else{
                        switch_c.setChecked(false);//当APP没有连接到单片机时，默认此按钮点击无效
                    }
                }
            }
        });

        //抽湿开关按钮
        final Switch switch_t=findViewById(R.id.switch_t);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            switch_t.setShowText(true);//按钮上默认显示文字
        }
        switch_t.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                {
                    switch_t.setSwitchTextAppearance(MainActivity.this,R.style.s_true);//开关样式
                    switch_t.setShowText(true);//显示文字on
                    if (send(send_order[2],-1)){
                        showDialog("开启抽湿");
                    }else{
                        switch_t.setChecked(false);//当APP没有连接到单片机时，默认此按钮点击无效
                    }
                }else{
                    switch_t.setSwitchTextAppearance(MainActivity.this,R.style.s_false);//开关样式
                    switch_t.setShowText(true);
                    if (send(send_order[3],-1)){
                        // flag=false;
                        showDialog("关闭抽湿");
                    }else{
                        switch_t.setChecked(false);//当APP没有连接到单片机时，默认此按钮点击无效
                    }
                }
            }
        });
    }

    //开启子线程
    private Runnable mRunnable = new Runnable() {

        @Override
        public void run() {
            String msgText = IPText.getText().toString();
            if(msgText.length()<=0)//IP和端口号不能为空
            {
                Message msg = new Message();
                msg.what = 5;
                mHandler.sendMessage(msg);
                return;
            }
            int start = msgText.indexOf(":");//IP和端口号格式不正确
            if((start==-1)||(start+1>=msgText.length()))
            {
                Message msg = new Message();
                msg.what = 6;
                mHandler.sendMessage(msg);
                return;
            }
            String sIP= msgText.substring(0,start);
            String sPort = msgText.substring(start+1);
            int port = Integer.parseInt(sPort);

            BufferedReader mBufferedReaderClient;//从字符输入流中读取文本并缓冲字符，以便有效地读取字符，数组和行
            try
            {
                //连接服务器
                mSocketClient = new Socket();//创建Socket
                SocketAddress socAddress = new InetSocketAddress(sIP, port);//设置ip地址和端口号
                mSocketClient.connect(socAddress, 2000);//设置超时时间为2秒

                //取得输入、输出流
                mBufferedReaderClient =new BufferedReader(new InputStreamReader(mSocketClient.getInputStream()));
                mPrintWriterClient=new PrintWriter(mSocketClient.getOutputStream(),true);

                //连接成功,把这个好消息告诉主线程，配合主线程进行更新UI。
                Message msg = new Message();
                msg.what = 1;
                mHandler.sendMessage(msg);

            }catch (Exception e) {
                //如果连接不成功，也要把这个消息告诉主线程，配合主线程进行更新UI。
                Message msg = new Message();
                msg.what = 2;
                mHandler.sendMessage(msg);
                return;
            }
            char[] buffer = new char[256];
            int count ;

            while(true)
            {
                try
                {
                    if((count = mBufferedReaderClient.read(buffer))>0)//当读取服务器发来的数据时
                    {
                        res = getInfoBuff(buffer,count)+"\n";//接收到的内容格式转换成字符串
                        //当读取服务器发来的数据时，也把这个消息告诉主线程，配合主线程进行更新UI。
                        Message msg = new Message();
                        msg.what = 4;
                        mHandler.sendMessage(msg);
                    }
                }catch (Exception e) {
                    // TODO: handle exception
                    //当读取服务器发来的数据错误时，也把这个消息告诉主线程，配合主线程进行更新UI。
                    Message msg = new Message();
                    msg.what = 3;
                    mHandler.sendMessage(msg);
                }
            }
        }
    };

    /**
     * 在安卓里面，涉及到网络连接等耗时操作时，不能将其放在UI主线程中，
     * 需要添加子线程，在子线程进行网络连接，这就涉及到安卓线程间的通信了，
     * 用Handle来实现。这里的子线程也就是 mThreadClient
     *
     *   handle的定义： 主要接受子线程发送的数据, 并用此数据配合主线程更新UI.
     *   解释: 当应用程序启动时，Android首先会开启一个主线程 (也就是UI线程) ,
     *   主线程为管理界面中的UI控件，进行事件分发, 比如说, 你要是点击一个 Button,
     *   Android会分发事件到Button上，来响应你的操作。  如果此时需要一个耗时的操作，
     *   例如: 联网读取数据，或者读取本地较大的一个文件的时候，你不能把这些操作放在主线程中，
     *   如果你放在主线程中的话，界面会出现假死现象, 如果5秒钟还没有完成的话，
     *   会收到Android系统的一个错误提示  "强制关闭".  这个时候我们需要把这些耗时的操作，
     *   放在一个子线程中,更新UI只能在主线程中更新，子线程中操作是危险的. 这个时候，
     *   Handler就出现了来解决这个复杂的问题，由于Handler运行在主线程中(UI线程中)，
     *   它与子线程可以通过Message对象来传递数据，这个时候，
     *   Handler就承担着接受子线程传过来的(子线程用sedMessage()方法传弟)Message对象，
     *   里面包含数据, 把这些消息放入主线程队列中，配合主线程进行更新UI。
     */
    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler()
    {
        @SuppressLint("SetTextI18n")
        public void handleMessage(Message msg)
        {
            super.handleMessage(msg);
            if(msg.what==4)//当读取到服务器发来的数据时，收到了子线程的消息，而且接收到的字符串我们给它定义的是res
            {
                char []arrs;
                arrs=res.toCharArray();//接收来自服务器的字符串，把字符串转成字符数组
                if (arrs.length>=3) {
                    if (arrs[0]=='T'){//如果字符数组的首位是T，说明接收到的信息是温湿度 T25

                        temp.setText("温湿度："+arrs[1]+arrs[2]+"℃");
                    }

                    else if (arrs[0]=='M'){//如果字符数组的首位是T，说明接收到的信息是气体浓度M66

                       mq.setText("气体浓度："+arrs[1]+arrs[2]+"%");
                    }
                }else {
                    showDialog("收到格式错误的数据:"+res);
                }
            }else if (msg.what==2){
                showDialog("连接失败，服务器走丢了");
                startButton.setText("开始连接");


            }else if (msg.what==1){
                showDialog("连接成功！");
                warning_show.setText("已连接智能衣柜\n");
                IPText.setEnabled(false);//锁定ip地址和端口号
                isConnecting = true;
                startButton.setText("停止连接");
                textView.setText("已连接单片机");
            }else if (msg.what==3){
                warning_show.setText("已断开连接\n");


            }else if (msg.what==5){
                warning_show.setText("IP和端口号不能为空\n");
            }
            else if (msg.what==6){
                warning_show.setText("IP地址不合法\n");
            }
        }
    };

    /**
     * 窗口提示
     * @param msg
     */
    private  void showDialog(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setTitle(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.create().show();
    }

    /**
     * 字符数组转字符串
     * @param buff
     * @param count
     * @return
     */
    private String getInfoBuff(char[] buff,int count)
    {
        char[] temp = new char[count];
        System.arraycopy(buff, 0, temp, 0, count);
        return new String(temp);
    }

    /**
     * 发送函数
     * @param msg
     * @param position
     * @return
     */
    private boolean send(String msg,int position){
        if(isConnecting&&mSocketClient!=null){
            if ((position==-1)){
                try
                {
                    mPrintWriterClient.print(msg);
                    mPrintWriterClient.flush();
                    return true;
                }catch (Exception e) {
                    // TODO: handle exception
                    Toast.makeText(MainActivity.this, "发送异常"+e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

        }else{
            showDialog("您还没有连接衣柜呢！");
        }
        return false;
    }


}

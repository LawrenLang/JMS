import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.BlobMessage;

import javax.jms.*;
import javax.jms.Message;
import javax.swing.*;
import java.io.*;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.activemq.*;


public class Application {
    public static final String BROKER_URL = "tcp://127.0.0.1:61616";
    public static Scanner scanner = new Scanner(System.in);
    public static String appName;
    GT g = GT.getInstance();
    //相当于一个数据库（其实是一个队列）



    public static void main(String[] args) throws Exception {

        System.out.println("请输入本地名称：");
        appName=scanner.nextLine();
        String prefix="From "+appName+": ";
        Application app=new Application(appName);

        String msg, dst, fname;
        int type;
        while(true) {
            System.out.println("请输入目的地：\n(群发为\"all\")");
            dst = scanner.nextLine();
            System.out.println("请输入信息类型\n" +
                    "(1:text)\n"+
                    "(2:transport file)");
            type=scanner.nextInt();
            scanner.nextLine();
            switch (type) {
                case 1:
                    System.out.println("请输入内容：");
                    msg = scanner.nextLine();
                    sendMessage(dst, prefix + msg);
                    System.out.println("内容已发送！");
                    break;
                case 2:
                    transportFile(dst);
                    break;
                default:
                    break;
            }
        }

    }

    public Application (String name) throws JMSException {
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URL);
        Connection connection = null;
        Session session = null;
        //2. 获取一个连接
        connection = connectionFactory.createConnection();
        //接收消息，需要将连接启动一下，才可以接收到消息
        connection.start();
        //3. 创建一个Session 第一个参数：是否是事务消息 第二个参数：消息确认机制（自动确认还是手动确认）
        session = connection.createSession(Boolean.FALSE, Session.AUTO_ACKNOWLEDGE);
        //4. 有了session之后，就可以创建消息，目的地，生产者和消费者
        //目的地
        Destination destination = session.createQueue(name);
        //消费者
        MessageConsumer messageConsumer = session.createConsumer(destination);
        Session finalSession = session;
        messageConsumer.setMessageListener(new MessageListener(){
            public void onMessage(Message message) {
                System.out.print(">>");

                if(message instanceof TextMessage) {

                    TextMessage Tmessage=(TextMessage) message;
                    try {
                        String msg = Tmessage.getText();
                        System.out.println(Tmessage.getText());
                        System.out.println("[中文翻译]：" + g.translateText(msg, "auto","zh_cn"));
                        String now =  "[转发自 + " + appName + " ]" + Tmessage.getText();
                        String des = JOptionPane.showInputDialog("输入转发对象：");
                        sendMessage(des, now);
                        System.out.println("转发成功");
                    } catch (Exception e) {
                    }
                } else if(message instanceof BytesMessage) {
                    BytesMessage bytesMessage = (BytesMessage) message;
                    try {
                        System.out.println("收到来自[" + message.getJMSDestination().toString() +  "]的文件");
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                    long startTime = System.currentTimeMillis();
                    try {
                        String fileName = bytesMessage
                                .getStringProperty("FILE.NAME");
                        System.out.println("文件名：" + fileName);
                        System.out.println("文件接收请求处理：" + fileName + "，文件大小："
                                + bytesMessage.getBodyLength()
                                + " 字节");

                        JFileChooser fileChooser = new JFileChooser();
                        fileChooser.setDialogTitle("请指定文件保存位置");
                        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                        if (fileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION)
                            return;


                        File file = fileChooser.getSelectedFile();
                        File nfile = new File(file.getPath() + "\\" + fileName);

                        OutputStream os = new FileOutputStream(nfile);
                        System.out.println("开始接收文件：" + fileName);


                        byte[] buff = new byte[256];
                        int len = 0;
                        while ((len = bytesMessage.readBytes(buff)) > 0) {
                            os.write(buff, 0, len);
                        }
                        //获得回执地址
                        Destination recall_destination = message.getJMSReplyTo();
                        // 创建回执消息
                        TextMessage textMessage = finalSession.createTextMessage(" [" + appName + "] 已接收文件：" + fileName);
                        // 以上收到消息之后，从新创建生产者，然后在回执过去
                        MessageProducer producer = finalSession.createProducer(recall_destination);
                        producer.send(textMessage);
                        os.close();
                        System.out.println("完成文件接收：" + fileName);

                    } catch (IOException | JMSException e) {
                        System.out.println("传输失败！");
                    }
                }
            }
        });

        /*群发消息接收*/
        //创建目的地
        Destination topicDestination = session.createTopic("default-topic");
        //创建消费者对象，指定目的地
        MessageConsumer topicMessageConsumer = session.createConsumer(topicDestination);
        //接收消息
        Session finalSession1 = session;
        topicMessageConsumer.setMessageListener(new MessageListener(){
            public void onMessage(Message message) {

                if(message instanceof TextMessage) {
                    System.out.print(">>[all]");
                    TextMessage Tmessage=(TextMessage) message;
                    try {
                        System.out.println(Tmessage.getText());
                    } catch (Exception e) {
                    }
                } else if(message instanceof BytesMessage) {
                    BytesMessage bytesMessage = (BytesMessage) message;
                    try {
                        System.out.println("收到来自[" + message.getJMSDestination().toString() +  "]的群发文件");
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                    long startTime=System.currentTimeMillis();
                    try {
                        String fileName = bytesMessage
                                .getStringProperty("FILE.NAME");
                        System.out.println("文件名：" + fileName);
                        System.out.println("文件接收请求处理：" + fileName + "，文件大小："
                                + bytesMessage.getBodyLength()
                                + " 字节");

                        JFileChooser fileChooser = new JFileChooser();
                        fileChooser.setDialogTitle("请指定文件保存位置");
                        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                        if (fileChooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION)
                            return;


                        File file = fileChooser.getSelectedFile();
                        File nfile = new File(file.getPath() + "\\" + fileName);

                        OutputStream os = new FileOutputStream(nfile);
                        System.out.println("开始接收文件：" + fileName);


                        byte[] buff = new byte[256];
                        int len = 0;
                        while ((len = bytesMessage.readBytes(buff)) > 0) {
                            os.write(buff, 0, len);
                        }
                        //获得回执地址
                        Destination recall_destination = message.getJMSReplyTo();
                        // 创建回执消息
                        TextMessage textMessage = finalSession1.createTextMessage(" [" + appName + "] 已接收文件：" + fileName);
                        // 以上收到消息之后，从新创建生产者，然后在回执过去
                        MessageProducer producer = finalSession1.createProducer(recall_destination);
                        producer.send(textMessage);
                        os.close();
                        System.out.println("完成文件接收：" + fileName);

                    } catch (IOException | JMSException e) {
                        System.out.println("传输失败！");
                    }

                }

            }
        });

    }



    public static void sendMessage(String DESTINATION,String text){
        //1 .创建一个连接工厂
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URL);
        Connection connection = null;
        Session session = null;
        MessageProducer messageProducer = null;
        try {
            //2. 获取一个连接
            connection = connectionFactory.createConnection();
            //3. 创建一个Session 第一个参数：是否是事务消息 第二个参数：消息确认机制（自动确认还是手动确认）
            session = connection.createSession(Boolean.FALSE, Session.AUTO_ACKNOWLEDGE);
            //4. 有了session之后，就可以创建消息，目的地，生产者和消费者
            Message message = session.createTextMessage(text);

            Destination destination;
            switch (DESTINATION) {
                case "all":
                    destination = session.createTopic("default-topic");
                    break;
                default:
                    destination = session.createQueue(DESTINATION);
                    break;
            }
            //生产者
            messageProducer = session.createProducer(destination);
            //发消息 没有返回值，是非阻塞的
            messageProducer.send(message);
        } catch (JMSException e) {
            e.printStackTrace();
        }finally{
            try {
                if(messageProducer != null){
                    messageProducer.close();
                }
                if(session != null){
                    session.close();
                }
                if(connection != null){
                    connection.close();
                }
            }catch (JMSException e) {
                e.printStackTrace();
            }
        }
    }
    public static void transportFile(String DESTINATION) throws JMSException, IOException {


        // 选择文件
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择文件");
        if (fileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fileChooser.getSelectedFile();


        // 获取 ConnectionFactory
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
                "tcp://localhost:61616");

        // 创建 Connection
        Connection connection = connectionFactory.createConnection();
        connection.start();

        // 创建 Session
        ActiveMQSession session = (ActiveMQSession) connection.createSession(
                false, Session.AUTO_ACKNOWLEDGE);

        // 创建 Destination
        Destination destination;
        switch (DESTINATION) {
            case "all":
                destination = session.createTopic("default-topic");
                break;
            default:
                destination = session.createQueue(DESTINATION);
                break;
        }

        BytesMessage bytesMessage=session.createBytesMessage();
        bytesMessage.setStringProperty("FILE.NAME", file.getName());


        InputStream is = new FileInputStream(file);
        byte[] buffer=new byte[is.available()];
        is.read(buffer);
        bytesMessage.writeBytes(buffer);

        Destination reback = session.createQueue(appName);
        MessageConsumer messageConsumer = session.createConsumer(reback);
        messageConsumer.setMessageListener(new reListener(session));
        //将回执地址写到消息
        bytesMessage.setJMSReplyTo(reback);
        bytesMessage.setStringProperty("FileName",file.getName());



        // 创建 Producer
        MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);// 设置为非持久性
        // 设置持久性的话，文件也可以先缓存下来，接收端离线再连接也可以收到文件



        // 7. 发送文件
        producer.send(bytesMessage);
        System.out.println(bytesMessage.toString());
        System.out.println("完成文件发送：" + file.getName());
        is.close();
        producer.close();
        session.close();
        connection.close(); // 不关闭 Connection, 程序则不退出
    }


    private static class reListener implements MessageListener {
        ActiveMQSession session=null;
        private Executor threadPool = Executors.newFixedThreadPool(8);;

        public reListener(ActiveMQSession session) {
            this.session=session;
        }

        @Override
        public void onMessage(Message message) {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    TextMessage tx= (TextMessage) message;
                    try {
                        System.out.println(tx.getText()+"----");
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}








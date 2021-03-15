import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.BlobMessage;

import javax.jms.*;
import javax.jms.Message;
import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;
import org.apache.activemq.*;

public class Application {
    public static final String BROKER_URL = "tcp://127.0.0.1:61616";
    public static Scanner scanner = new Scanner(System.in);
    public static String appName;
    //相当于一个数据库（其实是一个队列）
    public static void main(String[] args) throws JMSException {

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
        messageConsumer.setMessageListener(new MessageListener(){
            public void onMessage(Message arg0) {
                System.out.print(">>");
                TextMessage message=(TextMessage) arg0;
                try {
                    System.out.println(message.getText());
                } catch (Exception e) {
                }
            }
        });

        /*群发消息接收*/
        //创建目的地
        Destination topicDestination = session.createTopic("default-topic");
        //创建消费者对象，指定目的地
        MessageConsumer topicMessageConsumer = session.createConsumer(topicDestination);
        //接收消息
        topicMessageConsumer.setMessageListener(new MessageListener(){
            public void onMessage(Message arg0) {
                System.out.print(">>[all]");
                TextMessage message=(TextMessage) arg0;
                try {
                    System.out.println(message.getText());
                } catch (Exception e) {
                }
            }
        });

        /*文件传输*/
        // 创建 Session
        /*Session fileSession = connection.createSession(false,
                Session.AUTO_ACKNOWLEDGE);*/

        // 创建 Destinatione
        Destination fileDestination = session.createQueue(appName+".File.Transport");

        // 创建 Consumer
        MessageConsumer fileConsumer = session.createConsumer(fileDestination);

        // 注册消息监听器，当消息到达时被触发并处理消息
        fileConsumer.setMessageListener(new MessageListener() {

            // 监听器中处理消息
            public void onMessage(Message message) {
                if (message instanceof BlobMessage) {
                    BlobMessage blobMessage = (BlobMessage) message;
                    try {
                        String fileName = blobMessage
                                .getStringProperty("FILE.NAME");
                        System.out.println("文件接收请求处理：" + fileName + "，文件大小："
                                + blobMessage.getLongProperty("FILE.SIZE")
                                + " 字节");

                        JFileChooser fileChooser = new JFileChooser();
                        fileChooser.setDialogTitle("请指定文件保存位置");
                        fileChooser.setSelectedFile(new File(fileName));
                        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            File file = fileChooser.getSelectedFile();
                            OutputStream os = new FileOutputStream(file);

                            System.out.println("开始接收文件：" + fileName);
                            InputStream inputStream = blobMessage
                                    .getInputStream();

                            // 写文件，你也可以使用其他方式
                            byte[] buff = new byte[256];
                            int len = 0;
                            while ((len = inputStream.read(buff)) > 0) {
                                os.write(buff, 0, len);
                            }
                            os.close();
                            System.out.println("完成文件接收：" + fileName);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
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
    public static void transportFile(String DESTINATION ) throws JMSException {
        System.out.println("debug1");
        // 选择文件
        JFileChooser fileChooser = new JFileChooser();
        System.out.println("debug3");
        fileChooser.setDialogTitle("选择文件");
        System.out.println("debug4");
        if (fileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        System.out.println("debug5");
        File file = fileChooser.getSelectedFile();
        System.out.println("debug2");

        // 获取 ConnectionFactory
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
                "tcp://localhost:61616?jms.blobTransferPolicy.defaultUploadUrl=http://localhost:8161/fileserver/");

        // 创建 Connection
        Connection connection = connectionFactory.createConnection();
        connection.start();

        // 创建 Session
        ActiveMQSession session = (ActiveMQSession) connection.createSession(
                false, Session.AUTO_ACKNOWLEDGE);

        // 创建 Destination
        Destination destination = session.createQueue(DESTINATION+".File.Transport");

        // 创建 Producer
        MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);// 设置为非持久性
        // 设置持久性的话，文件也可以先缓存下来，接收端离线再连接也可以收到文件

        // 构造 BlobMessage，用来传输文件
        BlobMessage blobMessage = session.createBlobMessage(file);
        blobMessage.setStringProperty("FILE.NAME", file.getName());
        blobMessage.setLongProperty("FILE.SIZE", file.length());
        System.out.println("开始发送文件：" + file.getName() + "，文件大小："
                + file.length() + " 字节");

        // 7. 发送文件
        producer.send(blobMessage);
        System.out.println("完成文件发送：" + file.getName());

        producer.close();
        session.close();
        connection.close(); // 不关闭 Connection, 程序则不退出
    }
}







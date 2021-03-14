import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;
import java.util.Scanner;

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

        String msg, dst;
        while(true) {
            System.out.println("请输入目的地：");
            dst = scanner.nextLine();
            System.out.println("请输入内容：");
            msg = scanner.nextLine();
            sendMessage(dst,prefix+msg);
            System.out.println("内容已发送！");
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
            //目的地
            Destination destination = session.createQueue(DESTINATION);
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
}







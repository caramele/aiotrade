package org.aiotrade.lib.amqp

import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import java.util.logging.Level
import java.util.logging.Logger
import org.aiotrade.lib.util.reactors.Event

/**
 *
 * @author Caoyuan Deng
 */
class AMQPSubscriber(factory: ConnectionFactory, exchange: String, isAutoAck: Boolean = true, durable: Boolean = false) extends AMQPDispatcher(factory, exchange) {
  private val log = Logger.getLogger(this.getClass.getName)

  object Queue {
    def apply(name: String) = new Queue(name, false, false, true)
    def apply(name: String, durable: Boolean, exclusive: Boolean, autoDelete: Boolean) =
      new Queue(name, durable, exclusive, autoDelete)

    def unapply(queue: Queue) = Some((queue.name, queue.durable, queue.exclusive, queue.autoDelete))
  }

  class Queue private (val name: String, val durable: Boolean, val exclusive: Boolean, val autoDelete: Boolean) {
    override def equals(that: Any) = that match {
      case x: Queue => x.name == name
      case _ => false
    }

    override def hashCode = name.hashCode

    override def toString =
      "Queue(" + name + ", durable=" + durable + ", exclusive=" + exclusive + ", autoDelete=" + autoDelete + ")"
  }

  case class Topic(name: String, bindingQueue: String) {
    override def toString = "Topic(" + name + " ~> " + bindingQueue + ")"
  }

  case class ConsumeQueue(queue: Queue, isDefult: Boolean) extends Event
  case class SubscribeTopic(topic: Topic) extends Event
  case class UnsubscribeTopic(topic: Topic) extends Event

  private var _defaultQueue: Option[Queue] = None
  private var _consumingQueues = Map[String, Queue]()
  private var _subscribedTopics = Set[Topic]()

  /**
   * @Note Since we cannot guarantee when will AMQPConnected received (before or after consumeQueue/subscribeTopic calls),
   * we should be aware the concurrent issue. So use actor + messages here to avoid concurrent issue
   */
  reactions += {
    case AMQPConnected => 
      // When reconnecting, should resubscribing existed queues and topics:
      log.info("Got AMQPConnected event, try to (re)subscribing : " + _consumingQueues + ", " + _subscribedTopics)
      val default = _defaultQueue
      _consumingQueues foreach {case (qname, queue) => doConsumeQueue(queue)}
      _defaultQueue = default
      _subscribedTopics foreach doSubscribeTopic
    case ConsumeQueue(queue: Queue, isDefault) =>
      doConsumeQueue(queue)
    case SubscribeTopic(topic: Topic) =>
      doSubscribeTopic(topic)
    case UnsubscribeTopic(topic: Topic) =>
      doUnsubscribeTopic(topic)
  }

  override protected def configure(channel: Channel): Option[Consumer] = {
    Some(new AMQPConsumer(channel, isAutoAck))
  }

  /**
   * Consumer queue. If this queue does not exist yet, also delcare it here.
   */
  def consumeQueue(name: String, durable: Boolean = false, exclusive: Boolean = false, autoDelete: Boolean = true, isDefault: Boolean = false) {
    publish(ConsumeQueue(Queue(name, durable, exclusive, autoDelete), isDefault))
  }

  def subscribeTopic(name: String, bindingQueue: String = null) {
    publish(SubscribeTopic(Topic(name, bindingQueue)))
  }

  def unsubscribeTopic(name: String, bindingQueue: String = null) {
    publish(UnsubscribeTopic(Topic(name, bindingQueue)))
  }

  private def doConsumeQueue(queue: Queue, isDefault: Boolean = false) {
    if (!isConnected) {
      log.severe("Should connect before consume " + queue)
    } else {
      _defaultQueue = if (isDefault) {
        Some(queue)
      } else {
        Some(_defaultQueue.getOrElse(queue))
      }

      _consumingQueues += (queue.name -> queue)
      for (ch <- channel; cs <- consumer) {
        log.log(Level.INFO, "AMQPSubscriber exchange declaring [" + exchange + ", direct, " +  durable + "]")
        ch.exchangeDeclare(exchange, "direct", durable)

        // @Todo We need a non-exclusive queue, so when reconnected, this queue can be used by the new created connection.
        // string queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments
        try {
          ch.queueDeclare(queue.name, queue.durable, queue.exclusive, queue.autoDelete, null)
        } catch {
          case ex => log.log(Level.SEVERE, ex.getMessage, ex)
        }
      
        ch.basicConsume(queue.name, cs.asInstanceOf[AMQPConsumer].isAutoAck, cs)
        log.info("Declared queue: " + queue.name + ". The defaultQueue=" + _defaultQueue.map(_.name))
      }
    }
  }

  private def doSubscribeTopic(topic: Topic) {
    if (!isConnected) {
      log.severe("Should connect before subscribe " + topic)
    } else {
      if (_consumingQueues.isEmpty) {
        log.severe("At least one queue should be declared before subscribe " + topic)
      } else {
        val queueOpt = topic.bindingQueue match {
          case null => _defaultQueue
          case x => _consumingQueues.get(x)
        }

        queueOpt match {
          case None => log.severe("Queue does not in consuming when subscribe " + topic + " on it.")
          case Some(q) =>
            _subscribedTopics += Topic(topic.name, q.name)
            for (ch <- channel) {
              ch.queueBind(q.name, exchange, topic.name)
              log.info("Subscribed topic: " + topic)
            }
        }
      }
    }

  }

  private def doUnsubscribeTopic(topic: Topic) {
    _subscribedTopics -= topic // remove it whatever

    if (!isConnected) {
      log.warning("Should connect before unsubscribe " + topic)
    } else {
      val queueOpt = topic.bindingQueue match {
        case null => _defaultQueue
        case x => _consumingQueues.get(x)
      }

      queueOpt match {
        case None => log.warning("Queue does not in consuming when ubsubscribe " + topic + " on it.")
        case Some(q) =>
          for (ch <- channel) {
            ch.queueUnbind(q.name, exchange, topic.name)
          }
      }
    }
  }

}



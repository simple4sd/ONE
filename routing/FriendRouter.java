package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.List;






import java.util.Set;
import java.util.Vector;

import routing.util.RoutingInfo;
import util.Tuple;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.DTNHost;
import core.SimClock;
import core.Connection;
import core.SimError;


public class FriendRouter extends ActiveRouter {
	
	/** Friend router's setting namespace ({@value})*/
	public static final String FRIEND_NS = "FriendRouter";
	
	//public static final String PERIOD ="period";
	
	/** The period length used to compute the metrics, T = {@value}*/
	public static final double period = 8000;
	
	public int count;
	
	/** friendship metric to every node*/
	private Map<Set<DTNHost>, Double> metrics;
	/** the records of contact and disconnect to every node*/
	private Map<Set<DTNHost>, List<Double>> timeRecords;
	
	//private Map<Set<DTNHost>, Double> lamda;
	
	//private Map<DTNHost, Integer> destdrop = new HashMap<DTNHost, Integer>();
	private Map<DTNHost, List<Integer>> localdrop;
	private Map<DTNHost, Double> destprob;
	//private static Double leastTransTime; 
	
	private double starttime = 0;
	private double update = 21600.0;
	
	public FriendRouter(Settings s) {
		super(s);
		
		//Settings friendSettings = new Settings(FRIEND_NS);
		initVal();
	}
	 
	private void initVal() {
		//leastTransTime = Message.;
		metrics = new HashMap<Set<DTNHost>, Double>(); 
		timeRecords = new HashMap<Set<DTNHost>, List<Double>>();
		localdrop = new HashMap<DTNHost, List<Integer>>();
		destprob = new HashMap<DTNHost, Double>();
		//lamda = new HashMap<Set<DTNHost>, Double>();
		//count = 1;
	}
	
	protected FriendRouter(FriendRouter r) {
		super(r);
		this.metrics = r.metrics;
		this.timeRecords = r.timeRecords;
		//this.lamda = r.lamda;
		this.localdrop = r.localdrop;
		//this.count = r.count;
		this.destprob = r.destprob;
	}
	/**
	 * Once the connection has changed, update the metircs.
	 */
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		DTNHost otherhost = con.getOtherNode(getHost()); 
		Set<DTNHost> hostSet = new HashSet<DTNHost>();
		hostSet.add(getHost());
		hostSet.add(otherhost);
	
		updateMetrics(hostSet, SimClock.getTime(), con.isUp());
			
		if(con.isUp()) {
			updateCopyPath(getHost(), con.getOtherNode(getHost()));
		}
		
		Double t = SimClock.getTime();
		
		if(t - starttime > update ) {
			starttime += update;
			
			Map<DTNHost, Double> newprob = new HashMap<DTNHost, Double>();
			
			Iterator<DTNHost> iter = localdrop.keySet().iterator();
			
			while(iter.hasNext()) {
				DTNHost host = iter.next();
				List<Integer> list = localdrop.get(host);
				
				double drop = 0;
				if(list.get(0) != 0)
				  drop = (double)list.get(1)/(double)list.get(0);
				newprob.put(host, drop);
			}
			destprob = newprob;
			localdrop.clear();
		}
		
//		Set<Set<DTNHost>> key = timeRecords.keySet();
//			
//		//System.out.println(" It's the host \t"+ this.getHost().getAddress());
//		for(Iterator it = key.iterator(); it.hasNext();) {
//			Set<DTNHost> host = (Set<DTNHost>)it.next();
//			for(DTNHost h:host) {
//			//System.out.print(h.getAddress()+"---");
//			}
//			for(Iterator its = timeRecords.get(host).iterator(); its.hasNext();) 
//			{
//				Double t = (Double)its.next();
//				//System.out.print( t+ ", ");
//			}
//			//System.out.println("__end");
//		}
			
	}
	
	public void updateCopyPath(DTNHost from, DTNHost to) {
		Collection<Message> c1 = from.getMessageCollection();
		Collection<Message> c2 = to.getMessageCollection();
		
		Iterator<Message> iter1 = c1.iterator();
		
		while(iter1.hasNext()) {
			Message m1 = iter1.next();
			Iterator<Message> iter2 = c2.iterator();
			while(iter2.hasNext()) {
				Message m2 = iter2.next();
				if(m1.getId() == m2.getId()) {
					Map<DTNHost, Double> cp1 = m1.getCopyPath();
					Map<DTNHost, Double> cp2 = m2.getCopyPath();
					
					Iterator<DTNHost> iter = cp1.keySet().iterator();
					while(iter.hasNext()) {
						DTNHost host = iter.next();
						if(cp2.containsKey(host) && cp2.get(host) < cp1.get(host)) {
							m2.addNodeOnCopyPath(host, cp1.get(host));
						} else if(!cp2.containsKey(host)) {
							m2.addNodeOnCopyPath(host, cp1.get(host));
						}
					}
					
					cp2 = m2.getCopyPath();
					iter = cp2.keySet().iterator();
					
					while(iter.hasNext()) {
						DTNHost h = iter.next();
						m1.addNodeOnCopyPath(h, cp2.get(h));
					}
				}
			}
		}
	}
	
	public void updateMetrics(Set<DTNHost> host, Double time, Boolean connect) {
		updateRecords(host, time, connect);
		if(connect) {
			List<Double> li = timeRecords.get(host);
			
			Double disconnectSum = 0.0;
			Double connectSum = 0.0;
			int i = li.size();
			for(; i > 2; i -= 2) {
				Double curDisLen = li.get(i-1) - li.get(i-2);
				disconnectSum += 0.5 * curDisLen * curDisLen;
				
				Double curConLen = li.get(i-2) - li.get(i-3);
				connectSum += -2 * Math.sqrt(curConLen);
				//connectSum += -4 * Math.sqrt(curConLen);
			}
			
			Double cutTime = li.get(li.size() - 1) - period;
			if(1 == i) {
				Double lastDisLen = li.get(0) - cutTime;
				disconnectSum += 0.5 * lastDisLen * lastDisLen;
			} else {
				Double lastDisLen = li.get(1) - li.get(0);
				disconnectSum += 0.5 * lastDisLen * lastDisLen;
				
				Double lastConLen = li.get(0) - cutTime;
				connectSum = -2 * Math.sqrt(lastConLen);
			}	
			if (disconnectSum + connectSum > 0) {
				metrics.put(host, period/(disconnectSum + connectSum));
			} else {
				metrics.put(host, -(disconnectSum+connectSum) + 0.1);
			}
		}
		
	}
	
	/**
	 * update the timeRecords to keep the current records of T 
	 * @param host The according host
	 * @param time The changed time we want to record
	 */
	public void updateRecords(Set<DTNHost> host, Double time, Boolean up) {
		/*
		 * if the status is disconnected and the difference less than
		 * the default transmit time.
		 */
		//if(!up && )
//		Set<DTNHost> hostSet = new HashSet<DTNHost>();
//		hostSet.add(getHost());
//		hostSet.add(host);
		
		if(timeRecords.containsKey(host)) {
			timeRecords.get(host).add(time);
		}
		else {
			List<Double> newList = new ArrayList<Double>();
			newList.add(time);
			timeRecords.put(host, newList);
		}
		List<Double> newList = timeRecords.get(host);
		Double cutTime = newList.get(newList.size() - 1) - period;
		
		Iterator<Double> iter  = newList.iterator();
		
		while(iter.hasNext()) {
			Double t = iter.next();
			if(t < cutTime) {
				iter.remove();
			}
		}
			
		
//		if( false ) {	
//			count++;
//			Vector<Double> len = new Vector<Double>();
//			Object[] arr = timeRecords.get(host).toArray();
//			
//			for (int j = arr.length - 1; j >= 1; j -= 2) {
//				len.addElement((Double)arr[j] - (Double)arr[j-1]);
//			}
//			double sum = 0;
//			for (int i=0; i < len.size(); i++) {
//				sum += len.get(i);
//			}
//			//System.out.println("Step into lamda");
//			lamda.put(host, len.size()/sum);
//		}
		
//		for(Double t : newList) {
//			if(t > cutTime)
//				return;
//			else
//				newList.remove(newList.indexOf(t));
//		}
	}
	@Override
	public void update() {
		super.update();
		
		if(!canStartTransfer() || isTransferring()) {
			return;
		}
	
		if(exchangeDeliverableMessages() != null) {
			return;
		}
	
			tryOtherMessages(); 
	}
	
	public double getMetricFor(Set<DTNHost> host) { 
		// make sure metrics are updated before getting
		if (metrics.containsKey(host)) {
			return metrics.get(host);
		}
		else {
			return 0;
		}
	}

	public double getDeliverProb(DTNHost host)
	{
		if(destprob.containsKey(host))
			return destprob.get(host);
		else
			return 0.0;
	}
	
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages = 
				new ArrayList<Tuple<Message, Connection>>();
		
		Collection<Message> msgCollection = getMessageCollection();
		/* for all connected hosts collect all messages that have a higher
		   metric of delivery by the other host */
		for(Connection con: getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			FriendRouter othRouter = (FriendRouter)other.getRouter();
			
			//System.out.println("run now in tryOtherMessage 1");
			if (othRouter.isTransferring()) {
				continue;
			}
			
			for (Message m: msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue;
				}
				
				Set<DTNHost> localSet = new HashSet<DTNHost>();
				localSet.add(m.getTo());
				localSet.add(getHost());
				
				Set<DTNHost> othSet = new HashSet<DTNHost>();
				othSet.add(m.getTo());
				othSet.add(othRouter.getHost());
				//CHANGE
				if (othRouter.getMetricFor(localSet) < this.getMetricFor(othSet) ) {
					messages.add(new Tuple<Message, Connection>(m,con));
				}
			}
		}
		
		//System.out.println("run now in tryOtherMessage 2222");
		//System.out.println(messages.size());
		if (messages.size() == 0) {
			
			return null;
		}
		
//		if ( SimClock.getTime() > 9000) {
//			messages = filterMessageByBuffer(messages);
//		}
		System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
		Collections.sort(messages, new TupleComparator());
		//System.out.println("run now in tryOtherMessage 33333333");
		
		return tryMessagesForConnected(messages);
	}
	
	public Map<DTNHost, List<Integer>> getdrop() {
		return localdrop;
	}
	
	public List<Tuple<Message, Connection>> 
	filterMessageByBuffer(List<Tuple<Message,
			Connection>> m) {
		List<Tuple<Message, Connection>> result = 
				new ArrayList<Tuple<Message, Connection>>();
		
		Iterator<Tuple<Message, Connection>> iter = 
				m.listIterator();
		
		while(iter.hasNext()) {
			Tuple<Message, Connection> mc  = iter.next();
			Message message = mc.getKey();
			Connection con = mc.getValue();
			
			Set<DTNHost> hostSet = new HashSet<DTNHost>();
			hostSet.add(con.getOtherNode(getHost()));
			hostSet.add(message.getTo());
			
			Map<DTNHost, List<Integer>> otherdrop = ((FriendRouter)con.getOtherNode(
					getHost()).getRouter()).getdrop();
			
			if (otherdrop.containsKey(message.getTo()) == false){
					break;
			}
			
//			System.out.println("Step into the informal filter.");
			List<Integer> list = otherdrop.get(
					message.getTo());

			double drop = (double)list.get(1)/(double)list.get(0);
			
//			System.out.println("drop = " +  (1 - drop));
			if(drop < 0.5) {
				result.add(new Tuple<Message, Connection>(message, con));
			}
		}
		return result;
	}
	
 	/** 
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the 
	 * connection (GRTRMax)
	 */
	private class TupleComparator implements Comparator 
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			Set<DTNHost> firstSet = new HashSet<DTNHost>();
			firstSet.add(getHost());
			firstSet.add(tuple1.getKey().getTo());
			double p1 = ((FriendRouter)tuple1.getValue().
					getOtherNode(getHost()).getRouter()).getMetricFor(
					firstSet);
			// -"- tuple2...
			Set<DTNHost> secondSet = new HashSet<DTNHost>();
			secondSet.add(getHost());
			secondSet.add(tuple2.getKey().getTo());
			
			double p2 = ((FriendRouter)tuple2.getValue().
					getOtherNode(getHost()).getRouter()).getMetricFor(
					secondSet);

			// bigger probability should come first
			
			if (p2-p1 == 0) {
				/* equal probabilities -> let queue mode decide */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			}
			else if (p2-p1 < 0) {
				return -1;
			}
			else {
				return 1;
			}
		}
	}

	@Override 
	public Message messageTransferred(String id, DTNHost from) {
		
		Message m = super.messageTransferred(id, from);
		
		DTNHost localHost = this.getHost();
		Double deliverprob = ((FriendRouter)from.getRouter()).getDeliverProb(localHost);
		Message m1 = this.getMessage(m.getId());
		m1.addNodeOnCopyPath(from, deliverprob);

		
		DTNHost destHost = m.getTo();
		if(localHost == destHost ) return m;
		
		if(localdrop.get(destHost) == null) {
			List<Integer> list = new ArrayList<Integer>();
			list.add(0);
			list.add(0);
			
			localdrop.put(destHost, list);
		} 
			
		int receive = localdrop.get(destHost).get(0);
		localdrop.get(destHost).set(0, ++receive);		
		return m;
	}
	
	@Override
	public void deleteMessage(String id, boolean drop) {
		Message m = this.getMessage(id);
//		DTNHost localHost = getHost();
		DTNHost destHost = m.getTo();
//		if the source of message is the local node it will lead to nullpointer error.
		if(drop && m.getFrom() != getHost()) {
			//System.out.println("delete message now");
			int dropm = localdrop.get(destHost).get(1);
//			int receive = localdrop.get(destHost).get(0);
//			int left = receive - dropm;
			localdrop.get(destHost).set(1, ++dropm);
//			System.out.println("The number of dropped is @ " + receive + " " + dropm + " " + localHost+ " " + destHost +
//					"\t$" + left);
		}
		super.deleteMessage(id, drop);
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(metrics.size() + 
				" delivery prediction(s)");
		
		for (Map.Entry<Set<DTNHost>, Double> e : metrics.entrySet()) {
			Set<DTNHost> host = e.getKey();
			Double value = e.getValue();
			
			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", 
					host, value)));
		}
		
		top.addMoreInfo(ri);
		return top;
	}
	
	@Override
	public MessageRouter replicate() {
		FriendRouter r = new FriendRouter(this);
		return r;
	}
}

package Server;

import java.awt.Insets;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Queue;

import DBC.ConnectionFactory;
import DBC.CouponsDAO;
import DBC.DAOFactory;
import DBC.EquivalentBondDAO;
import DBC.MessageDAO;
import DBC.PromotionDAO;
import Member.MemberPO;
import Promotion.CouponsPO;
import Promotion.EquivalentBondPO;
import Promotion.PromotionPO;
import RMI.ResultMessage;
import RMI.UserAgent;

public class Routines implements Runnable, Observer {
	private ArrayList<UserAgent> userAgents = null;
	private static Routines routines = null;
	private Queue<String> logQueue = null;

	private Routines() {
		UserPool.registry(this);
		userAgents = new ArrayList<UserAgent>();
		logQueue = new LinkedList<String>();
	};

	public static Routines getInstance() {
		if (routines == null) {
			synchronized (Routines.class) {
				if (routines == null) {
					routines = new Routines();
				}
			}
		}
		return routines;
	}

	@Override
	public void run() {
		while (true) {
			long loopStart = System.currentTimeMillis();
			checkOnline();
			checkBackUp();
			checkBirth();
			checkPromotion();
			writeLog();
			long loopEnd = System.currentTimeMillis();
			long sleeptime = Const.ROUTINE * 1000 - (loopEnd - loopStart);
			try {
				Thread.sleep(sleeptime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void checkOnline() {
		for (UserAgent agent : userAgents) {
			long deadtime = System.currentTimeMillis() - agent.lastRequest;
			if (deadtime > Const.TIMEOUT * 60 * 1000) {
				UserPool.disconnect(agent);
			}
		}
	}

	private void checkBackUp() {
		String path = getClass().getResource("").getPath();
		int index = path.lastIndexOf("bin");
		path = path.substring(1, index);
		path = path + Const.BACKUPPATH;
		path = path.replaceAll("%20", " ");
		File backupFile = new File(path);
		long last = backupFile.lastModified();
		if (System.currentTimeMillis() - last > Const.BACKUP * 24 * 60 * 60
				* 1000) {
			dumpSQL();
		}
	}

	private void checkBirth() {
		String[] times = Const.LAST_BIRTH.split("-");
		int year = Integer.parseInt(times[0]);
		int month = Integer.parseInt(times[1]);
		int day = Integer.parseInt(times[2]);
		Calendar now = Calendar.getInstance();
		if (now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) == month
				&& now.get(Calendar.DAY_OF_MONTH) == day) {
			return;
		}
		triggerBirth();
		Const.store("LAST_BIRTH",
				now.get(Calendar.YEAR) + "-" + now.get(Calendar.MONTH) + "-"
						+ now.get(Calendar.DAY_OF_MONTH));
	}

	private void checkPromotion() {
		ResultMessage promotionMessage = DAOFactory.getPromotionDAO()
				.getPromotionList();
		ResultMessage memberMessage = DAOFactory.getMemberDAO().getMembers();
		if (!promotionMessage.isInvokeSuccess()
				|| !memberMessage.isInvokeSuccess()) {
			return;
		}
		CouponsDAO couponsDAO = DAOFactory.getCouponsDAO();
		EquivalentBondDAO equivalentBondDAO = DAOFactory.getEquivalentBondDAO();
		MessageDAO messageDAO = DAOFactory.getMessageDAO();
		ArrayList<PromotionPO> promotions = promotionMessage.getResultSet();
		ArrayList<MemberPO> members = memberMessage.getResultSet();
		Connection con = ConnectionFactory.getConnection();
		String querysql = "select * from promotionhistory where promotionid=? and memberid=?";
		PreparedStatement queryps = null;
		ResultSet resultSet = null;
		String insertsql = "insert into promotionhistory(promotionid,memberid) values(?,?)";
		PreparedStatement insertps = null;
		try {
			queryps = con.prepareStatement(querysql);
			insertps = con.prepareStatement(insertsql);
			for (PromotionPO promotion : promotions) {
				for (MemberPO member : members) {
					if (member.getIntegral() < promotion.getLeastIntegral()) {
						continue;
					}
					queryps.setInt(1, promotion.getPromotionID());
					queryps.setInt(2, member.getID());
					resultSet = queryps.executeQuery();
					resultSet.last();
					if (resultSet.getRow() != 0) {
						continue;
					}
					double discountRate = promotion.getDiscountRate();
					double equivalentDenomination = promotion
							.getEquivalentDenomination();
					if (discountRate != 0) {
						couponsDAO.addCoupons(new CouponsPO(-1, member.getID(),
								discountRate, promotion.getEndDate(), false));
						messageDAO.addMessage(member.getID(), "您获得了"
								+ promotion.getName() + "促销的折扣券");
					}
					if (equivalentDenomination != 0) {
						equivalentBondDAO
								.addEquivalentBond(new EquivalentBondPO(-1,
										member.getID(), promotion
												.getBondUseLimit(),
										equivalentDenomination, promotion
												.getEndDate(), false));
						messageDAO.addMessage(member.getID(), "您获得了"
								+ promotion.getName() + "促销的折扣券");
					}
					insertps.setInt(1, promotion.getPromotionID());
					insertps.setInt(2, member.getID());
					insertps.executeUpdate();
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void writeLog() {
		BufferedWriter br = null;
		try {
			br = new BufferedWriter(new FileWriter(new File(
					Const.LOG_ACCESS_PATH), true));
		} catch (IOException e) {
			e.printStackTrace();
		}
		String log = null;
		while ((log = logQueue.poll()) != null) {
			try {
				br.write(log + "\r\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			br.flush();
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void triggerBirth() {
		ResultMessage memberMessage = DAOFactory.getMemberDAO()
				.getBirthMembers();
		ResultMessage birthMessage = DAOFactory.getPromotionDAO()
				.queryPromotion(1);
		if (!memberMessage.isInvokeSuccess() || !birthMessage.isInvokeSuccess()) {
			return;
		}
		CouponsDAO couponsDAO = DAOFactory.getCouponsDAO();
		EquivalentBondDAO equivalentBondDAO = DAOFactory.getEquivalentBondDAO();
		MessageDAO messageDAO = DAOFactory.getMessageDAO();

		PromotionPO birth = (PromotionPO) birthMessage.getResultSet().get(0);
		ArrayList<MemberPO> members = memberMessage.getResultSet();
		double discountRate = birth.getDiscountRate();
		double equivalentDenomination = birth.getEquivalentDenomination();
		int memberID = 0;
		if (discountRate != 0) {
			for (MemberPO member : members) {
				memberID = member.getID();
				couponsDAO.addCoupons(new CouponsPO(-1, memberID, discountRate,
						birth.getEndDate(), false));
				messageDAO.addMessage(memberID, "您获得了" + birth.getName()
						+ "促销的折扣券");
			}
		}
		if (equivalentDenomination != 0) {
			for (MemberPO member : members) {
				memberID = member.getID();
				equivalentBondDAO.addEquivalentBond(new EquivalentBondPO(-1,
						memberID, birth.getBondUseLimit(),
						equivalentDenomination, birth.getEndDate(), false));
				messageDAO.addMessage(memberID, "您获得了" + birth.getName()
						+ "促销的折扣券");
			}
		}
	}

	public void log(String log) {
		logQueue.add(log);
	}

	public boolean dumpSQL() {
		String path = getClass().getResource("").getPath();
		int index = path.lastIndexOf("bin");
		path = path.substring(1, index);
		path = path + Const.BACKUPPATH;
		path = path.replaceAll("%20", "^ ");
		String command = "cmd /c " + getMYSQLPath() + "mysqldump -u "
				+ Const.dbuser + " -p" + Const.dbpass + " --database obss > "
				+ path;
		try {
			Runtime.getRuntime().exec(command);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public String getMYSQLPath() {
		Runtime runtime = Runtime.getRuntime();
		Process process = null;
		try {
			process = runtime
					.exec("reg query "
							+ "HKEY_LOCAL_MACHINE\\SYSTEM\\CURRENTCONTROLSET\\SERVICES\\MYSQL /v ImagePath");
		} catch (IOException e) {
			e.printStackTrace();
		}
		BufferedReader in = new BufferedReader(new InputStreamReader(
				process.getInputStream()));
		String out = "";
		try {
			while ((out = in.readLine()) != null) {
				if (out.matches("(.*)ImagePath(.*)")) {
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (out == null) {
			return null;
		}
		int index1 = out.indexOf(":");
		int index2 = out.indexOf("mysql");
		return out.substring(index1 - 1, index2).replaceAll(" ", "\" \"");
	}

	public boolean loadSQL() {
		String path = getClass().getResource("").getPath();
		int index = path.lastIndexOf("bin");
		path = path.substring(1, index);
		path = path + Const.BACKUPPATH;
		path = path.replaceAll("%20", " ");
		String command = "cmd /c " + getMYSQLPath() + "mysql -u "
				+ Const.dbuser + " -p" + Const.dbpass + " < " + path;
		try {
			Runtime.getRuntime().exec(command);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean initSQL() {
		String path = getClass().getResource("").getPath();
		int index = path.lastIndexOf("bin");
		path = path.substring(1, index);
		path = path + Const.SQLPATH;
		path = path.replaceAll("%20", " ");
		String command = "cmd /c " + getMYSQLPath() + "mysql -u "
				+ Const.dbuser + " -p" + Const.dbpass + " < " + path;
		try {
			Runtime.getRuntime().exec(command);
		} catch (IOException e) {
			e.printStackTrace();
			command = "cmd /c " + getMYSQLPath() + "mysql -u" + Const.dbuser
					+ " -p" + Const.dbpass + " < " + "\"" + path + "\"";
			try {
				Runtime.getRuntime().exec(command);
			} catch (IOException e1) {
				e1.printStackTrace();
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void update(Observable o, Object arg) {
		userAgents = (ArrayList<UserAgent>) arg;
	}
}

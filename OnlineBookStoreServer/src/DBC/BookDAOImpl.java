package DBC;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;

import Book.BookPO;
import RMI.ResultMessage;

public class BookDAOImpl implements BookDAO {

	@Override
	public ResultMessage fill(ArrayList<BookPO> books) {
		try {
			for (BookPO book : books) {
				book = (BookPO) queryBookByISBN(book.getISBN()).getResultSet()
						.get(0);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return new ResultMessage(false, null, "fill book failed");
		}
		return new ResultMessage(true, books, "fill finished");
	}

	private ArrayList<BookPO> map(ResultSet resultSet) {
		ArrayList<BookPO> polist = null;
		String name = null, ISBN = null, author = null, press = null, description = null, directoryID = null;
		Calendar publishDate = null;
		double price = 0, specialPrice = 0;
		try {
			resultSet.last();
			int len = 0;
			if ((len = resultSet.getRow()) != 0) {
				polist = new ArrayList<BookPO>();
				resultSet.beforeFirst();
				for (int i = 0; i < len; i++) {
					resultSet.next();
					name = resultSet.getString(1);
					ISBN = resultSet.getString(2);
					author = resultSet.getString(3);
					description = resultSet.getString(4);
					directoryID = resultSet.getString(5);
					press = resultSet.getString(6);
					publishDate = Calendar.getInstance();
					publishDate.setTimeInMillis(resultSet.getDate(7).getTime());
					price = resultSet.getDouble(8);
					specialPrice = resultSet.getDouble(9);
					polist.add(new BookPO(name, ISBN, author, press,
							description, directoryID, publishDate, price,
							specialPrice));
				}
				return polist;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public ResultMessage addBook(BookPO bookPO) {
		ResultMessage isExist = queryBookByISBN(bookPO.getISBN());
		if (isExist.isInvokeSuccess()) {
			return new ResultMessage(false, null, "该ISBN已被占用，请检查输入");
		}
		Connection con = ConnectionFactory.getConnection();
		String sql = "insert into book(name,isbn,author,press,description,directoryID,publishdate,price,specialprice) values(?,?,?,?,?,?,?,?,?)";
		PreparedStatement ps;
		try {
			ps = con.prepareStatement(sql);
			ps.setString(1, bookPO.getName());
			ps.setString(2, bookPO.getISBN());
			ps.setString(3, bookPO.getAuthor());
			ps.setString(4, bookPO.getPress());
			ps.setString(5, bookPO.getDescription());
			ps.setString(6, bookPO.getDirectoryID());
			ps.setDate(7, new java.sql.Date(bookPO.getPublishDate().getTime()
					.getTime()));
			ps.setDouble(8, bookPO.getPrice());
			ps.setDouble(9, bookPO.getSpecialPrice());
			int row = ps.executeUpdate();
			con.close();
			if (row != 0) {
				return new ResultMessage(true, null, "添加图书成功");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return new ResultMessage(false, null, "添加图书失败，请重新再试");
	}

	@Override
	public ResultMessage deleteBook(String bookISBN) {
		ResultMessage isExist = queryBookByISBN(bookISBN);
		if (!isExist.isInvokeSuccess()) {
			return new ResultMessage(false, null, "该图书不存在，请检查输入");
		}
		Connection con = ConnectionFactory.getConnection();
		String sql = "delete from book where isbn = ?";
		PreparedStatement ps;
		try {
			ps = con.prepareStatement(sql);
			ps.setString(1, bookISBN);
			int row = ps.executeUpdate();
			con.close();
			if (row != 0) {
				return new ResultMessage(true, null, "删除图书成功");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return new ResultMessage(false, null, "删除图书失败，请重新再试");
	}

	@Override
	public ResultMessage updateBook(BookPO bookPO) {
		ResultMessage isExist = queryBookByISBN(bookPO.getISBN());
		if (!isExist.isInvokeSuccess()) {
			return new ResultMessage(false, null, "该图书不存在，请检查输入");
		}
		Connection con = ConnectionFactory.getConnection();
		String sql = "update book set name=?,author=?,press?,description=?,directoryID=?,publishdate=?,price=?,specialprice=? where isbn=?";
		PreparedStatement ps;
		try {
			ps = con.prepareStatement(sql);
			ps.setString(1, bookPO.getName());
			ps.setString(2, bookPO.getAuthor());
			ps.setString(3, bookPO.getPress());
			ps.setString(4, bookPO.getDescription());
			ps.setString(5, bookPO.getDirectoryID());
			ps.setDate(6, new java.sql.Date(bookPO.getPublishDate().getTime()
					.getTime()));
			ps.setDouble(7, bookPO.getPrice());
			ps.setDouble(8, bookPO.getSpecialPrice());
			ps.setString(9, bookPO.getISBN());
			int row = ps.executeUpdate();
			con.close();
			if (row != 0) {
				return new ResultMessage(true, null, "修改图书成功");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return new ResultMessage(false, null, "修改图书失败，请重新再试");
	}

	@Override
	public ResultMessage queryBookByISBN(String bookISBN) {
		Connection con = ConnectionFactory.getConnection();
		String sql = "select * from book where ISBN=?";
		PreparedStatement ps;
		ResultSet resultSet = null;
		try {
			ps = con.prepareStatement(sql);
			ps.setString(1, bookISBN);
			resultSet = ps.executeQuery();
			con.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		ArrayList<BookPO> result = map(resultSet);
		if (result != null) {
			return new ResultMessage(true, result, "查询成功 结果返回");
		}
		return new ResultMessage(false, result, "无此书存在");
	}

	@Override
	public ResultMessage searchBookByName(String keywords) {
		Connection con = ConnectionFactory.getConnection();
		ResultSet resultSet = null;
		String sql = "select * from book where name like ?";
		PreparedStatement ps;
		try {
			ps = con.prepareStatement(sql);
			ps.setString(1, "%" + keywords + "%");
			resultSet = ps.executeQuery();
			con.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		ArrayList<BookPO> result = map(resultSet);
		if (result != null) {
			return new ResultMessage(true, result, "查询成功 结果返回");
		}
		return new ResultMessage(false, result, "无符合的书存在");
	}

	@Override
	public ResultMessage searchBookByAuthor(String keywords) {
		Connection con = ConnectionFactory.getConnection();
		ResultSet resultSet = null;
		String sql = "select * from book where author like ?";
		PreparedStatement ps;
		try {
			ps = con.prepareStatement(sql);
			ps.setString(1, "%" + keywords + "%");
			resultSet = ps.executeQuery();
			con.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		ArrayList<BookPO> result = map(resultSet);
		if (result != null) {
			return new ResultMessage(true, result, "查询成功 结果返回");
		}
		return new ResultMessage(false, result, "无符合的书存在");
	}

	@Override
	public ResultMessage searchBookByMulti(String keywords) {
		Connection con = ConnectionFactory.getConnection();
		ResultSet resultSet = null;
		String sql = "select * from book where description like ? or name like ? or author like ?";
		PreparedStatement ps;
		try {
			ps = con.prepareStatement(sql);
			ps.setString(1, "%" + keywords + "%");
			ps.setString(2, "%" + keywords + "%");
			ps.setString(3, "%" + keywords + "%");
			resultSet = ps.executeQuery();
			con.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		ArrayList<BookPO> result = map(resultSet);
		if (result != null) {
			return new ResultMessage(true, result, "查询成功 结果返回");
		}
		return new ResultMessage(false, result, "无符合的书存在");
	}

	@Override
	public ResultMessage queryAllBooks() {
		Connection con = ConnectionFactory.getConnection();
		ArrayList<BookPO> polist = new ArrayList<BookPO>();
		ResultSet resultSet = null;
		String sql = "select * from book";
		try {
			Statement statement = con.createStatement();
			resultSet = statement.executeQuery(sql);
			con.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		polist = map(resultSet);
		if (polist != null) {
			return new ResultMessage(true, polist, "查询成功 全部书本返回");
		}
		return new ResultMessage(false, null, "无图书在库");
	}

	@Override
	public ResultMessage queryBookByDirectory(int directoryID) {
		Connection con = ConnectionFactory.getConnection();
		String sql = "select * from book where directoryid=?";
		PreparedStatement ps;
		ResultSet resultSet = null;
		try {
			ps = con.prepareStatement(sql);
			ps.setInt(1, directoryID);
			resultSet = ps.executeQuery();
			con.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		ArrayList<BookPO> result = map(resultSet);
		if (result != null) {
			return new ResultMessage(true, result, "查询成功 结果返回");
		}
		return new ResultMessage(false, result, "无此书存在");
	}
}

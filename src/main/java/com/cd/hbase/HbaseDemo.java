package com.cd.hbase;


import com.alibaba.fastjson.JSON;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.BinaryPrefixComparator;
import org.apache.hadoop.hbase.filter.ByteArrayComparable;
import org.apache.hadoop.hbase.filter.ColumnPrefixFilter;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.FamilyFilter;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.MultipleColumnPrefixFilter;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.filter.QualifierFilter;
import org.apache.hadoop.hbase.filter.RegexStringComparator;
import org.apache.hadoop.hbase.filter.RowFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.filter.SubstringComparator;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HbaseDemo {

	private Configuration conf = null;
	private Connection connection = null;
	private Table table = null;

	@Before
	public void init() throws IOException {
		conf = HBaseConfiguration.create();
		conf.set("hbase.zookeeper.quorum", "cluster01,cluster02,cluster03");
		connection = ConnectionFactory.createConnection(conf);
		table = connection.getTable(TableName.valueOf(Bytes.toBytes("user_info")));
	}

	@Test
	public void testDrop() throws Exception{
//		HBaseAdmin admin = new HBaseAdmin(conf);
		Admin admin = connection.getAdmin();
		admin.disableTable(TableName.valueOf("account"));
		admin.deleteTable(TableName.valueOf("account"));
		admin.close();
	}

	@Test
	public void testPut() throws Exception{
		Put p = new Put(Bytes.toBytes("rk0003"));
//		p.add("base_info".getBytes(), "name".getBytes(), "zhangwuji".getBytes());
		p.addColumn("base_info".getBytes(), "name".getBytes(), "朱茵".getBytes());
		table.put(p);
		table.close();
	}

	@Test
	public void testGet() throws Exception{
//		HTable table = new HTable(conf, "user_info");
		Get get = new Get(Bytes.toBytes("rk0001"));
		get.setMaxVersions(5);
		Result result = table.get(get);
		List<Cell> cells = result.listCells();


		for (int i = 0; i < cells.size(); i++) {
			Cell cell = cells.get(i);
			System.out.println(new String(CellUtil.cloneFamily(cell)) + " => " + new String(CellUtil.cloneQualifier(cell)) + ": " + new String(CellUtil.cloneValue(cell)));
		}

		// table.get(gets);
		List<Get> gets = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			StringBuilder rk = new StringBuilder("rk000");
			get = new Get(Bytes.toBytes(rk.append(i).toString()));
			gets.add(get);
		}
		Result[] results = table.get(gets);
		for (int i = 0; i < results.length; i++) {
			Result tmpResult = results[i];
			Map<String, Object> map = new HashMap<>();
			for (Cell cell : tmpResult.rawCells()) {
				map.put(Bytes.toString(CellUtil.cloneQualifier(cell)), Bytes.toString(CellUtil.cloneValue(cell)));
//				System.out.println(Bytes.toString(CellUtil.cloneRow(cell)) + " => " + Bytes.toString(CellUtil.cloneFamily(cell)) + ":" +
//						Bytes.toString(CellUtil.cloneQualifier(cell)) + ":" + Bytes.toString(CellUtil.cloneValue(cell)));
			}
			System.out.println(Bytes.toString(tmpResult.getRow()) + " => " + JSON.toJSONString(map));
		}
		table.close();
	}

	/**
	 * scan
	 * @throws Exception
	 */
	@Test
	public void testScan() throws Exception{
		Scan scan = new Scan();
		ResultScanner scanner = table.getScanner(scan);
		for(Result r : scanner){
			//直接从result中取到某个特定的value
			byte[] value = r.getValue(Bytes.toBytes("base_info"), Bytes.toBytes("name"));
//			System.out.println(new String(value));
			System.out.println(Bytes.toString(r.getRow()) + " > " + Bytes.toString(value));
		}
		table.close();
	}

	/**
	 * 多种过滤条件的使用方法
	 * @throws Exception
	 */
	@Test
	public void testScanFilter() throws Exception{
		Scan scan = new Scan();
//		Scan scan = new Scan(Bytes.toBytes("rk0001"), Bytes.toBytes("rk0002"));

		//前缀过滤器----针对行键
		Filter filter = new PrefixFilter(Bytes.toBytes("rk"));

		//行过滤器
		ByteArrayComparable rowComparator = new BinaryComparator(Bytes.toBytes("rk0001"));
		RowFilter rf = new RowFilter(CompareOp.LESS_OR_EQUAL, rowComparator);

		/**
		 * 假设rowkey格式为：创建日期_发布日期_ID_TITLE
		 * 目标：查找  发布日期  为  2014-12-21  的数据
		 */
		rf = new RowFilter(CompareOp.EQUAL , new SubstringComparator("_2014-12-21_"));


		//单值过滤器 1 完整匹配字节数组
		new SingleColumnValueFilter("base_info".getBytes(), "name".getBytes(), CompareOp.EQUAL, "zhangsan".getBytes());
		//单值过滤器2 匹配正则表达式
		ByteArrayComparable comparator = new RegexStringComparator("zhang.");
		new SingleColumnValueFilter("info".getBytes(), "NAME".getBytes(), CompareOp.EQUAL, comparator);

		//单值过滤器2 匹配是否包含子串,大小写不敏感
		comparator = new SubstringComparator("wu");
		new SingleColumnValueFilter("info".getBytes(), "NAME".getBytes(), CompareOp.EQUAL, comparator);

		//键值对元数据过滤-----family过滤----字节数组完整匹配
		FamilyFilter ff = new FamilyFilter(
				CompareOp.EQUAL ,
				new BinaryComparator(Bytes.toBytes("base_info"))   //表中不存在inf列族，过滤结果为空
		);
		//键值对元数据过滤-----family过滤----字节数组前缀匹配
		ff = new FamilyFilter(
				CompareOp.EQUAL ,
				new BinaryPrefixComparator(Bytes.toBytes("inf"))   //表中存在以inf打头的列族info，过滤结果为该列族所有行
		);


		//键值对元数据过滤-----qualifier过滤----字节数组完整匹配

		filter = new QualifierFilter(
				CompareOp.EQUAL ,
				new BinaryComparator(Bytes.toBytes("na"))   //表中不存在na列，过滤结果为空
		);
		//表中存在以na打头的列name，过滤结果为所有行的该列数据
		filter = new QualifierFilter(
				CompareOp.EQUAL ,
				new BinaryPrefixComparator(Bytes.toBytes("na"))
		);

		//基于列名(即Qualifier)前缀过滤数据的ColumnPrefixFilter
		filter = new ColumnPrefixFilter("na".getBytes());

		//基于列名(即Qualifier)多个前缀过滤数据的MultipleColumnPrefixFilter
		byte[][] prefixes = new byte[][] {Bytes.toBytes("na"), Bytes.toBytes("me")};
		filter = new MultipleColumnPrefixFilter(prefixes);

		//为查询设置过滤条件
		scan.setFilter(filter);

		scan.addFamily(Bytes.toBytes("base_info"));
		//一行
//		Result result = table.get(get);
		//多行的数据
		ResultScanner scanner = table.getScanner(scan);
		for(Result r : scanner){
			/**
			 for(KeyValue kv : r.list()){
			 String family = new String(kv.getFamily());
			 System.out.println(family);
			 String qualifier = new String(kv.getQualifier());
			 System.out.println(qualifier);
			 System.out.println(new String(kv.getValue()));
			 }
			 */
			//直接从result中取到某个特定的value
			byte[] value = r.getValue(Bytes.toBytes("base_info"), Bytes.toBytes("name"));
			System.out.println(new String(value));
		}
		table.close();
	}


	@Test
	public void testDel() throws Exception{
		Delete del = new Delete(Bytes.toBytes("rk0001"));
//		del.deleteColumn(Bytes.toBytes("data"), Bytes.toBytes("pic"));
		table.delete(del);
		table.close();
	}



	public static void main(String[] args) throws Exception {
//		Configuration conf = HBaseConfiguration.create();
//		conf.set("hbase.zookeeper.quorum", "cluster01:2181,cluster02:2181,cluster03:2181");
////		HBaseAdmin admin = new HBaseAdmin(conf);
//		HBaseAdmin admin = new HBaseAdmin(conf);
//
//		TableName tableName = TableName.valueOf("person_info");
//		HTableDescriptor td = new HTableDescriptor(tableName);
//		HColumnDescriptor cd = new HColumnDescriptor("base_info");
//		cd.setMaxVersions(10);
//		td.addFamily(cd);
//		admin.createTable(td);
//
//		admin.close();

	}



}

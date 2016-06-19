package com.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.types.BSONTimestamp;

import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.Bytes;
import com.mongodb.CursorType;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongoCmdOptionsBuilder;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;

public class MongoOplogReader {

	private static int MONGO_PORT = 27018;
	
	private static String DEFAULT_CONNECTION_STRING = "mongodb://localhost:";
	
	private static MongodProcess MONGO;
	
	private static MongoClient mongoClient;
	
	private static MongoDatabase db;
	
	private static String DB_NAME = "test_db2";
	
	private static String COLNAME = "test_collection";
	
	private static String LOCALDB = "local";
	
	private static MongoDatabase localDB;
	
	private static String OPLOG = "oplog.$main";
//	private static String OPLOG = "oplog.rs";
	
	private static MongoCollection<Document> oplog;
	
	private static int DOCNUM = 3;
	
	private static BsonTimestamp lastTimeStamp = null;
	
	private static BSONTimestamp lastTS_old = null;
	
	public static void main(String[] args) {
      initEmbedMongo();
//	  connectMongo();
      initData();
      testOplog();
//      readOplogThread();
//      readOplogDBCursor();
      stopMongo();
	}

	private static void connectMongo() {
	  mongoClient = new MongoClient(new MongoClientURI(DEFAULT_CONNECTION_STRING + "27017"));
	}
	
	private static void testOplog() {
	  try {	
      //
	  List<Document> list = new ArrayList<>();
	  list = readOplog();
	  System.out.println("#items in oplog = " + list.size());
      // update 1 doc
	  String oldName = "user 1";
	  String newName = oldName + " renamed";
	  db.getCollection(COLNAME).updateOne(new Document("name", oldName),
	                                      new Document("$set", new Document("name", newName)));
	  // read oplog
	  list = readOplog();
	  System.out.println("#items in oplog after updating 1 item = " + list.size());
	  
      // delete 1 doc
	  db.getCollection(COLNAME).deleteOne(new Document("name", newName));
	  // read oplog
	  list = readOplog();
	  System.out.println("#items in oplog after deleting 1 item = " + list.size());
	  } catch (Exception e) {
		e.printStackTrace();
	  }
	}

	private static void stopMongo() {
//      db.drop();
	  MONGO.stop();
	}

	private static List<Document> readOplog() throws Exception {
		List<Document> opLogList = new ArrayList<>();

		Document filter = new Document();
		filter.put("ns", db.getName() + "." + COLNAME);
		filter.put("op", new Document("$in", Arrays.asList("i", "u", "d")));
		if (lastTimeStamp != null) {
			filter.put("ts", new Document("$gt", lastTimeStamp));
		}
		Document projection = new Document("ts", 1).append("op", 1).append("o", 1);
		Document sort = new Document("$natural", 1);

	try(	
		MongoCursor<Document> cursor = oplog.find(filter)
				                              .projection(projection)
//				                              .batchSize(15)
				                              .sort(sort)
                                              .cursorType(CursorType.TailableAwait)
//                                              .cursorType(CursorType.Tailable)
//				                              .cursorType(CursorType.NonTailable)
				                              .noCursorTimeout(true)
//				                              .maxTime(2, SECONDS)
//				                              .maxAwaitTime(20, SECONDS)
				                              .iterator())//;
	{
		System.out.println("--- before iterating in readOplog, #items to read = " + oplog.count(filter));
//		while (cursor.hasNext()) {
		while (true) {
//			Thread.sleep(20000);
			Document document = cursor.tryNext();
			if (document == null) break;
			opLogList.add(document);
			lastTimeStamp = (BsonTimestamp) document.get("ts");
			System.out.println("user: " + ((Document) document.get("o")).get("name") +
			 " - op: " + document.get("op"));
//		    Thread.sleep(500);
		}
		System.out.println("--- finish the iteration");
//		System.out.println("#items read = " + opLogList.size());
	}
		return opLogList;
	}

	private static void readOplogThread() {
		List<Document> opLogList = new ArrayList<>();

		Document filter = new Document();
		filter.put("ns", db.getName() + "." + COLNAME);
		filter.put("op", new Document("$in", Arrays.asList("i", "u", "d")));
		if (lastTimeStamp != null) {
			filter.put("ts", new Document("$gt", lastTimeStamp));
		}
		Document projection = new Document("ts", 1).append("op", 1).append("o", 1);
		Document sort = new Document("$natural", 1);

//	try(	
		MongoCursor<Document> cursor = oplog.find(filter)
				                              .projection(projection)
//				                              .batchSize(15)
				                              .sort(sort)
//                                              .cursorType(CursorType.TailableAwait)
                                              .cursorType(CursorType.Tailable)
//				                              .cursorType(CursorType.NonTailable)
				                              .noCursorTimeout(true)
//				                              .oplogReplay(true)
//				                              .maxTime(2, SECONDS)
//				                              .maxAwaitTime(20, SECONDS)
				                              .iterator();
//	{
	Runnable task = () -> {
		  System.out.println("--- before iteration");
		while (cursor.hasNext()) {
//			Thread.sleep(20000);
			Document document = cursor.next();
			if (document == null) break;
			opLogList.add(document);
			lastTimeStamp = (BsonTimestamp) document.get("ts");
			 System.out.println("user: " + ((Document) document.get("o")).get("name") +
			 " - op: " + document.get("op"));
		}
		System.out.println("--- end iteration");
		System.out.println("#items read = " + opLogList.size());
	};
		
		new Thread(task).start();
//	} catch (Exception e) {
//	  e.printStackTrace();
//	}
		
	}

	private static void readOplogDBCursor() {
		List<DBObject> opLogList = new ArrayList<>();

		DBObject filter = new BasicDBObject();
		filter.put("ns", db.getName() + "." + COLNAME);
		filter.put("op", new BasicDBObject("$in", Arrays.asList("i", "u", "d")));
		if (lastTS_old != null) {
			filter.put("ts", new BasicDBObject("$gt", lastTS_old));
		}
		DBObject projection = new BasicDBObject("ts", 1).append("op", 1).append("o", 1);
		DBObject sort = new BasicDBObject("$natural", 1);

		DBCollection oplog_old = mongoClient.getDB(LOCALDB).getCollection(OPLOG);
		
	try {
		System.out.println("#items in oplog = " + oplog_old.count(filter));
		DBCursor cursor = oplog_old
								   .find(filter, projection)
//			                       .batchSize(15)
			                       .addOption(Bytes.QUERYOPTION_TAILABLE | Bytes.QUERYOPTION_AWAITDATA)
			                       .addOption(Bytes.QUERYOPTION_NOTIMEOUT)
			                       .sort(sort);

	Runnable task = () -> {
        System.out.println("--- before iteration w/ tailable DBCursor");
        while(true) {
			DBObject document = cursor.tryNext();
			if (document == null) break;
			opLogList.add(document);
			lastTS_old = (BSONTimestamp) document.get("ts");
			System.out.println("user: " + ((DBObject) document.get("o")).get("name") +
			 " - op: " + document.get("op"));
		} // while
		System.out.println("--- end iteration");
		System.out.println("#items read = " + opLogList.size());
	};
		
		new Thread(task).start();
	} catch (Exception e) {
	  e.printStackTrace();
	}
		
	}
	
	private static void initData() {
	    db = mongoClient.getDatabase(DB_NAME);
	    oplog = mongoClient.getDatabase(LOCALDB).getCollection(OPLOG);
	    if (!db.listCollectionNames().into(new ArrayList<String>()).contains(COLNAME))
	      db.createCollection(COLNAME);
//	    db.createCollection(COLNAME, new CreateCollectionOptions().capped(true).sizeInBytes(20000));
	    
	    long curSeconds = System.currentTimeMillis()/1000;
	    lastTS_old = new BSONTimestamp(((Number) curSeconds).intValue(), 0);
	    System.out.println("lastTS_old = " + lastTS_old.getTime());
	    lastTimeStamp = new BsonTimestamp(lastTS_old.getTime(), lastTS_old.getInc());
	    
	    for (int i = 0; i < DOCNUM; i++) {
	      Document doc = new Document();
	      doc.put("name", "user " + (i + 1));
          doc.put("age", 18 + i);
	      db.getCollection(COLNAME).insertOne(doc);
	    }

//	    Document filter = new Document("ts", new Document("$gt", lastTimeStamp));
//	    FindIterable<Document> iter = db.getCollection(COLNAME).find(filter);
	    FindIterable<Document> iter = db.getCollection(COLNAME).find();
	    iter.forEach(new Block<Document>() {
	      @Override
	      public void apply(final Document doc) {
	        System.out.println("- name: " + doc.get("name") + " - age: " + doc.get("age"));
	      }
	    });
	}

	private static void initEmbedMongo() {
	  try {
        MongodStarter starter = MongodStarter.getDefaultInstance();
        IMongodConfig mongodConfig = new MongodConfigBuilder().version(Version.Main.PRODUCTION)
                                                              .net(new Net(MONGO_PORT, Network.localhostIsIPv6()))
                                                              .cmdOptions(new MongoCmdOptionsBuilder().master(true).build())
                                                              .build();
        MongodExecutable mongodExecutable = starter.prepare(mongodConfig);
        MONGO = mongodExecutable.start();
	  } catch (Exception e) {
		e.printStackTrace(); 
	  }
	  mongoClient = new MongoClient(new MongoClientURI(DEFAULT_CONNECTION_STRING + MONGO_PORT));
	}

}

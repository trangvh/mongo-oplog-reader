package com.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.bson.BsonTimestamp;
import org.bson.Document;

import com.mongodb.Block;
import com.mongodb.CursorType;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

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
	
	private static String DB_NAME = "test_db";
	
	private static String COLNAME = "test_collection";
	
	private static String LOCALDB = "local";
	
	private static MongoDatabase localDB;
	
	private static String OPLOG = "oplog.$main";
	
	private static MongoCollection<Document> oplog;
	
	private static int DOCNUM = 5;
	
	private static BsonTimestamp lastTimeStamp = null;
	
	public static void main(String[] args) {
      initEmbedMongo();
      initData();
      testOplog();
      stopMongo();
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
      db.drop();
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

		MongoCursor<Document> cursor = oplog.find(filter)
				                              .projection(projection)
				                              .sort(sort)
                                            .cursorType(CursorType.TailableAwait)
//                                            .cursorType(CursorType.Tailable)
//				                              .cursorType(CursorType.NonTailable)
				                              .noCursorTimeout(true)
				                              .maxTime(10, SECONDS)
				                              .maxAwaitTime(20, SECONDS)
				                              .iterator();
		while (cursor.hasNext()) {
			Document document = cursor.next();
			opLogList.add(document);
			lastTimeStamp = (BsonTimestamp) document.get("ts");
			 System.out.println("user: " + ((Document) document.get("o")).get("name") +
			 " - op: " + document.get("op"));
		}
		return opLogList;
	}

	private static void initData() {
	    db = mongoClient.getDatabase(DB_NAME);
	    oplog = mongoClient.getDatabase(LOCALDB).getCollection(OPLOG);
	    db.createCollection(COLNAME);
	    for (int i = 0; i < DOCNUM; i++) {
	      db.getCollection(COLNAME).insertOne(new Document().append("name", "user " + (i + 1))
	                                                        .append("age", 18 + i));
	    }
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

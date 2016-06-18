package com.example;

import java.io.IOException;

import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
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
	
	private static int DOCNUM = 5;
	
	public static void main(String[] args) {
      initEmbedMongo();
      initData();
      readOplog();
	}

	private static void readOplog() {
		
	}

	private static void initData() {
	    db.createCollection(COLNAME);
	    for (int i = 0; i < DOCNUM; i++) {
	      db.getCollection(COLNAME).insertOne(new Document().append("name", "user " + (i + 1))
	                                                        .append("age", 18 + i));
	    }
		
	}

	private static void initEmbedMongo() throws IOException {
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

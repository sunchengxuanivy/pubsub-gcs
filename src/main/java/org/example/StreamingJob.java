/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.example;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.gcp.pubsub.PubSubSource;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="https://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class StreamingJob {

    public static void main(String[] args) throws Exception {


        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.enableCheckpointing(1000);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);

        DeserializationSchema<String> deserializationSchema = new SimpleStringSchema();

//        KafkaSource<String> source = KafkaSource.<String>builder()
//                .setBootstrapServers("34.27.205.32:9092,35.188.39.91:9092")
//                .setTopics("quickstart-events")
//                .setGroupId("my-group")
//                .setStartingOffsets(OffsetsInitializer.earliest())
//                .setValueOnlyDeserializer(deserializationSchema)
//                .build();


        SourceFunction<String> pubsubSource = PubSubSource.newBuilder()
                .withDeserializationSchema(deserializationSchema)
                .withProjectName("transsion-poc-2022")
                .withSubscriptionName("test-flink-sub")
                .build();


//        DataStream<Tuple2<String, Integer>> dataStream = env
//                .readTextFile("/var/log/dataproc-startup-script.log")
//                .flatMap(new Splitter())
//                .keyBy(value -> value.f0)
//                .sum(1);

//        dataStream.writeAsText("gs://transsion-poc-dataproc-bucket01/flink-output",
//                org.apache.flink.core.fs.FileSystem.WriteMode.OVERWRITE);

        final StreamingFileSink<String> sink = StreamingFileSink
//                .forRowFormat(new Path("hdfs:///transsion-poc-dataproc-bucket01/flink-output-sink"),
                .forRowFormat(new Path("gs://transsion-poc-dataproc-bucket01/flink-output-sink"),
                        new SimpleStringEncoder<String>("UTF-8"))
                .withRollingPolicy(
                        DefaultRollingPolicy.builder()
                                .withRolloverInterval(TimeUnit.MINUTES.toMillis(5))
                                .withInactivityInterval(TimeUnit.MINUTES.toMillis(5))
                                .withMaxPartSize(1024 * 1024 * 1024)
                                .build()
                ).build();
//

        env.addSource(pubsubSource)
                .addSink(sink);
//        env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source").addSink(sink);


        env.execute("Window WordCount");
    }

    public static class Splitter implements FlatMapFunction<String, Tuple2<String, Integer>> {
        @Override
        public void flatMap(String sentence, Collector<Tuple2<String, Integer>> out) throws Exception {
            for (String word : sentence.split(" ")) {
                out.collect(new Tuple2<String, Integer>(word, 1));
            }
        }
    }

}
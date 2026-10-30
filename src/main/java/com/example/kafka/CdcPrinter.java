package com.example.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class CdcPrinter {

	public static void main(String[] args) {
		String bootstrap = System.getenv().getOrDefault("BOOTSTRAP_SERVERS", "127.0.0.1:19094");

		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "cdc-printer");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
			consumer.subscribe(List.of("customers.public.users", "customers.public.orders"));
			System.out.println("слушаю customers.public.users и customers.public.orders, bootstrap=" + bootstrap);

			while (true) {
				ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
				for (ConsumerRecord<String, String> record : records) {
					System.out.printf("%s partition=%d offset=%d key=%s%n%s%n%n",
							record.topic(), record.partition(), record.offset(), record.key(), record.value());
				}
			}
		}
	}
}

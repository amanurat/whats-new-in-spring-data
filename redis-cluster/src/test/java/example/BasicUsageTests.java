/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example;

import static org.hamcrest.core.Is.*;
import static org.hamcrest.core.IsCollectionContaining.*;
import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * {@link BasicUsageTests} shows general usage of {@link RedisTemplate} and {@link RedisOperations} in a clustered
 * environment.
 * 
 * @author Christoph Strobl
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AppConfig.class)
public class BasicUsageTests {

	@Autowired RedisTemplate<String, String> template;

	@Before
	public void setUp() {

		template.execute(new RedisCallback<String>() {

			@Override
			public String doInRedis(RedisConnection connection) throws DataAccessException {
				connection.flushDb();
				return "FLUSHED";
			}
		});
	}

	/**
	 * Operation executed on a single node and slot. <br />
	 * -&gt; {@code SLOT 5798} served by {@code 127.0.0.1:7379}
	 */
	@Test
	public void singleSlotOperation() {

		template.opsForValue().set("name", "rand al'thor"); // slot 5798
		assertThat(template.opsForValue().get("name"), is("rand al'thor"));
	}

	/**
	 * Operation executed on multiple nodes and slots. <br />
	 * -&gt; {@code SLOT 5798} served by {@code 127.0.0.1:7379} <br />
	 * -&gt; {@code SLOT 14594} served by {@code 127.0.0.1:7380}
	 */
	@Test
	public void multiSlotOperation() {

		template.opsForValue().set("name", "matrim cauthon"); // slot 5798
		template.opsForValue().set("nickname", "prince of the ravens"); // slot 14594

		assertThat(template.opsForValue().multiGet(Arrays.asList("name", "nickname")),
				hasItems("matrim cauthon", "prince of the ravens"));
	}

	/**
	 * Operation executed on a single node and slot because of pinned slot key <br />
	 * -&gt; {@code SLOT 5798} served by {@code 127.0.0.1:7379}
	 */
	@Test
	public void fixedSlotOperation() {

		template.opsForValue().set("{user}.name", "perrin aybara"); // slot 5474
		template.opsForValue().set("{user}.nickname", "wolfbrother"); // slot 5474

		assertThat(template.opsForValue().multiGet(Arrays.asList("{user}.name", "{user}.nickname")),
				hasItems("perrin aybara", "wolfbrother"));
	}

	/**
	 * Operation executed across the cluster to retrieve cumulated result. <br />
	 * -&gt; {@code KEY age} served by {@code 127.0.0.1:7379} <br />
	 * -&gt; {@code KEY name} served by {@code 127.0.0.1:7379} <br />
	 * -&gt; {@code KEY nickname} served by {@code 127.0.0.1:7380}
	 */
	@Test
	public void multiNodeOperation() {

		template.opsForValue().set("age", "23"); // slot 741;
		template.opsForValue().set("name", "rand al'thor"); // slot 5798
		template.opsForValue().set("nickname", "dragon reborn"); // slot 14594

		assertThat(template.keys("*"), hasItems("name", "nickname", "age"));
	}
}

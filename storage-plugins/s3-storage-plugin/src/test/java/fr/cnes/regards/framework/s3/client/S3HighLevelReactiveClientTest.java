package fr.cnes.regards.framework.s3.client;


import com.google.common.collect.ImmutableMap;
import fr.cnes.regards.framework.s3.domain.StorageCommand;
import fr.cnes.regards.framework.s3.domain.StorageCommandID;
import fr.cnes.regards.framework.s3.domain.StorageConfig;
import fr.cnes.regards.framework.s3.domain.StorageEntry;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.slf4j.LoggerFactory.getLogger;

@Ignore("Check error")
public class S3HighLevelReactiveClientTest {

    private static final Logger LOGGER = getLogger(S3HighLevelReactiveClientTest.class);
    static final String BUCKET = "bucket";
    static final ImmutableMap<String, List<Tuple2<String, InputStream>>> S3SETUP = ImmutableMap.of(BUCKET, List.empty());
    private static String s3Host;

    private static GenericContainer<?> s3;

    public static GenericContainer<?> simpleMinioContainer() {
        return new GenericContainer<>("minio/minio")
                .withExposedPorts(9000)
                .withNetworkAliases("s3")
                .withEnv(HashMap.of(
                        "MINIO_REGION", "us-east-1",
                        "MINIO_ACCESS_KEY", "mykey",
                        "MINIO_SECRET_KEY", "mysecret"
                ).toJavaMap())
                .withCommand("server --address 0.0.0.0:9000 /data");
    }

    public static final String KEY = "mykey";
    public static final String SECRET = "mysecret";
    public static final String REGION = "us-east-1";
    @ClassRule
    public static TestRule chain = RuleChain
            .outerRule(
                    s3 = simpleMinioContainer()
                            .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix("s3"))
            )
            .around(new ExternalResource() {
                @Override
                protected void before() {
                    s3Host = "http://" + s3.getContainerIpAddress() + ":" + s3.getMappedPort(9000);
                }
            })
            .around(new S3Rule(S3SETUP, () -> s3Host, REGION, KEY, SECRET));


    @Test
    public void testWriteReadDeleteSmall() {
        String rootPath = "some/root/path";

        StorageConfig config = StorageConfig.builder()
                .endpoint(s3Host)
                .bucket(BUCKET)
                .region(REGION)
                .key(KEY)
                .secret(SECRET)
                .rootPath(rootPath)
                .build();

        S3HighLevelReactiveClient client = new S3HighLevelReactiveClient(Schedulers.immediate(), 1024);

        Flux<ByteBuffer> buffers = DataBufferUtils.read(new ClassPathResource("small.txt"), new DefaultDataBufferFactory(), 1024)
                .map(DataBuffer::asByteBuffer);
        StorageCommandID cmdId = new StorageCommandID("askId", UUID.randomUUID());

        String entryKey = config.entryKey("small.txt");
        long size = 427L;
        StorageEntry entry = StorageEntry.builder()
                .checksum(Option.of(Tuple.of("MD5", "706126bf6d8553708227dba90694e81c")))
                .config(config)
                .size(Option.some(size))
                .fullPath(entryKey)
                .data(buffers)
                .build();

        client.check(StorageCommand.check(config, cmdId, entryKey))
                .block()
                .matchCheckResult(
                        present -> { fail("Should be absent"); return false; },
                        absent -> true,
                        unreachableStorage -> { fail("s3 unreachable"); return false; }
                );

        client.write(StorageCommand.write(config, cmdId, entryKey, entry))
                .block()
                .matchWriteResult(
                        success -> { assertThat(success.getSize()).isEqualTo(size); return true; },
                        unreachableStorage -> { fail("s3 unreachable"); return false; },
                        failure -> { fail(failure.toString()); return false; }
                );

        client.check(StorageCommand.check(config, cmdId, entryKey))
                .block()
                .matchCheckResult(
                        present -> true,
                        absent -> {  fail("Should be present"); return false; },
                        unreachableStorage -> { fail("s3 unreachable"); return false; }
                );

        client.read(StorageCommand.read(config, cmdId, entryKey))
            .block()
            .matchReadResult(
                pipe -> {
                    pipe.getEntry()
                            .doOnNext(e -> LOGGER.info("entry: {}", readString(e)))
                            .block(); return true; },
                unreachableStorage -> { fail("s3 unreachable"); return false; },
                failure -> { fail(failure.toString()); return false; }
            );

        client.delete(StorageCommand.delete(config, cmdId, entryKey))
                .block()
                .matchDeleteResult(
                        success -> true,
                        unreachable -> { fail("Delete failed: Unreachable"); return false; },
                        failure -> { fail("Delete failed"); return false; }
                );

        client.check(StorageCommand.check(config, cmdId, entryKey))
                .block()
                .matchCheckResult(
                        present -> { fail("Should be absent"); return false; },
                        absent -> true,
                        unreachableStorage -> { fail("s3 unreachable"); return false; }
                );

    }


    @Test
    public void testWriteBig() {
        String rootPath = "some/root/path";

        StorageConfig config = StorageConfig.builder()
                .endpoint(s3Host)
                .bucket(BUCKET)
                .region(REGION)
                .key(KEY)
                .secret(SECRET)
                .rootPath(rootPath)
                .build();

        S3HighLevelReactiveClient client = new S3HighLevelReactiveClient(Schedulers.immediate(), 5 * 1024 * 1024);

        long size = 10L * 1024L * 1024L + 512L;

        Flux<ByteBuffer> buffers = Flux.just(ByteBuffer.wrap(new byte[(int)size]));

        StorageCommandID cmdId = new StorageCommandID("askId", UUID.randomUUID());

        String entryKey = config.entryKey("big.txt");

        StorageEntry entry = StorageEntry.builder()
                .checksum(Option.of(Tuple.of("MD5", "706126bf6d8553708227dba90694e81c")))
                .config(config)
                .size(Option.some(size))
                .fullPath(entryKey)
                .data(buffers)
                .build();

        client.write(StorageCommand.write(config, cmdId, entryKey, entry))
                .block()
                .matchWriteResult(
                        success -> { assertThat(success.getSize()).isEqualTo(size); return true; },
                        unreachableStorage -> { fail("s3 unreachable"); return false; },
                        failure -> { fail(failure.toString()); return false; }
                );

        client.check(StorageCommand.check(config, cmdId, entryKey))
                .block()
                .matchCheckResult(
                        present -> true,
                        absent -> {  fail("Should be present"); return false; },
                        unreachableStorage -> { fail("s3 unreachable"); return false; }
                );

        client.read(StorageCommand.read(config, cmdId, entryKey))
                .block()
                .matchReadResult(
                        pipe -> {
                            pipe.getEntry()
                                    .doOnNext(e -> {
                                        int length = readString(e).length();
                                        LOGGER.info("entry: {}", length);
                                        assertThat(length).isEqualTo(size);
                                    })
                                    .block();
                            return true;
                        },
                        unreachableStorage -> { fail("s3 unreachable"); return false; },
                        failure -> { fail(failure.toString()); return false; }
                );

        client.delete(StorageCommand.delete(config, cmdId, entryKey))
                .block()
                .matchDeleteResult(
                        success -> true,
                        unreachable -> { fail("Delete failed: Unreachable"); return false; },
                        failure -> { fail("Delete failed"); return false; }
                );

        client.check(StorageCommand.check(config, cmdId, entryKey))
                .block()
                .matchCheckResult(
                        present -> { fail("Should be absent"); return false; },
                        absent -> true,
                        unreachableStorage -> { fail("s3 unreachable"); return false; }
                );

    }

    @NotNull
    private String readString(StorageEntry e) {
        return DataBufferUtils.join(e.getData().map(bb -> new DefaultDataBufferFactory().wrap(bb)))
                .block()
                .toString(StandardCharsets.UTF_8);
    }

}
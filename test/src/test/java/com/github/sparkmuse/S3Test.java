package com.github.sparkmuse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.deliveredtechnologies.terraform.api.TerraformApply;
import com.deliveredtechnologies.terraform.fluent.api.Terraform;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

class S3Test {

    private static final int LOCAL_STACK_PORT = 4566;

    private static LocalStackContainer container;
    private static Terraform terraform;

    @BeforeAll
    static void setup(@TempDir Path targetDir) throws Exception {
        container = new LocalStackContainer(DockerImageName.parse("localstack/localstack:0.14.4"))
                .withServices(LocalStackContainer.Service.values())
                .withExposedPorts(LOCAL_STACK_PORT);
        container.start();

        FileUtils.copyDirectory(Paths.get("").toAbsolutePath().getParent().toFile(), targetDir.toFile());
        var rootDir = targetDir.resolve("example");

        terraform = new Terraform()
                .withRootDir(rootDir.toString())
                .withProperties(Map.of(
                        TerraformApply.Option.tfVars.toString(),
                        String.format("url=http://%s:%s", container.getHost(), container.getMappedPort(LOCAL_STACK_PORT))
                ));
    }

    @AfterAll
    static void tearDown() throws Exception {
        terraform.getDestroy().execute(terraform.getProperties());
        container.stop();
    }

    @Test
    void createsBucket() throws Exception {

        terraform.init();
        terraform.getPlan().execute(terraform.getProperties());
        terraform.getApply().execute(terraform.getProperties());

        // Check outputs
        var execute = terraform.getOutput().execute(terraform.getProperties());
        var output = new ObjectMapper().readValue(execute, new TypeReference<Map<String, Map<String, String>>>() {
        });
        assertThat(output.get("bucket")).containsEntry("value", "sample-bucket");

        // Check values with client
        S3Client s3 = S3Client.builder()
                .endpointOverride(container.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(container.getAccessKey(), container.getSecretKey())))
                .region(Region.of(container.getRegion()))
                .build();
        var response = s3.listBuckets();

        assertThat(response.buckets()).extracting("name").containsExactly("sample-bucket");
    }
}

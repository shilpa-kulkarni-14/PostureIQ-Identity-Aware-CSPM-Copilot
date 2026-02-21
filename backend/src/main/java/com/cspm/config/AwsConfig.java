package com.cspm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "aws.scanner.enabled", havingValue = "true")
public class AwsConfig {

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    @Value("${aws.region:us-east-1}")
    private String region;

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (accessKeyId != null && !accessKeyId.isEmpty()
                && secretAccessKey != null && !secretAccessKey.isEmpty()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public IamClient iamClient(AwsCredentialsProvider credentialsProvider) {
        // IAM is a global service, always use us-east-1
        return IamClient.builder()
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public Ec2Client ec2Client(AwsCredentialsProvider credentialsProvider) {
        return Ec2Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}

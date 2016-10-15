package de.taimos.dvalin.notification.push;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreatePlatformEndpointRequest;
import com.amazonaws.services.sns.model.CreatePlatformEndpointResult;
import com.amazonaws.services.sns.model.InvalidParameterException;
import com.amazonaws.services.sns.model.PublishRequest;

import de.taimos.daemon.spring.annotations.ProdComponent;
import de.taimos.dvalin.cloud.aws.AWSClient;

/**
 * PushService implementation using AWS SNS
 * <br>
 * Needed AWS actions:
 * <ul>
 * <li>sns:CreatePlatformEndpoint</li>
 * <li>sns:Publish</li>
 * </ul>
 */
@ProdComponent
public class AmazonSNSPushService implements PushService {
    
    public static final Logger LOGGER = LoggerFactory.getLogger(AmazonSNSPushService.class);
    
    private static final String MESSAGE_STRUCTURE_JSON = "json";
    
    @AWSClient
    private AmazonSNSClient snsClient;
    
    @Value("${aws.pushApplicationARN.GCM:}")
    private String pushARN_GCM;
    @Value("${aws.pushApplicationARN.APNS:}")
    private String pushARN_APNS;
    @Value("${aws.pushApplicationARN.APNS_SANDBOX:}")
    private String pushARN_APNS_SANDBOX;
    
    @Override
    public String registerDevice(Platform platform, String deviceToken, String userData) {
        try {
            LOGGER.info("Creating platform endpoint with device token {} for platform", deviceToken, platform);
            
            CreatePlatformEndpointRequest cpeReq = new CreatePlatformEndpointRequest()
                .withPlatformApplicationArn(this.getApplicationARN(platform))
                .withToken(deviceToken)
                .withCustomUserData(userData);
            
            CreatePlatformEndpointResult cpeRes = this.snsClient.createPlatformEndpoint(cpeReq);
            return cpeRes.getEndpointArn();
        } catch (InvalidParameterException ipe) {
            AmazonEndpointCreationException creationException = new AmazonEndpointCreationException(ipe);
            if (creationException.didAlreadyExist()) {
                return creationException.getExistingARN();
            }
            throw creationException.getOriginalException();
        }
    }
    
    private String getApplicationARN(Platform platform) {
        String arn = null;
        switch (platform) {
        case GCM:
            arn = this.pushARN_GCM;
            break;
        case APNS:
            arn = this.pushARN_APNS;
            break;
        case APNS_SANDBOX:
            arn = this.pushARN_APNS_SANDBOX;
            break;
        }
        if (arn != null && !arn.isEmpty()) {
            return arn;
        }
        throw new RuntimeException("Missing application ARN for " + platform);
    }
    
    @Override
    public void sendNotification(String deviceId, PushMessageWrapper message) {
        try {
            PublishRequest req = new PublishRequest();
            req.setTargetArn(deviceId);
            req.setMessageStructure(MESSAGE_STRUCTURE_JSON);
            req.setMessage(message.toMessage());
            this.snsClient.publish(req);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

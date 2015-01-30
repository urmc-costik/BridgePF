package org.sagebionetworks.bridge.models.upload;

import javax.annotation.Nonnull;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.validators.UploadValidationStatusValidator;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * This class represents upload validation status and messages. It's created from an
 * {@link org.sagebionetworks.bridge.models.upload.Upload} object and is returned to users.
 */
@JsonDeserialize(builder = UploadValidationStatus.Builder.class)
public class UploadValidationStatus implements BridgeEntity {
    private final @Nonnull String id;
    private final @Nonnull List<String> messageList;
    private final @Nonnull UploadStatus status;

    /** Private constructor. All construction should go through the builder or through the from() methods. */
    private UploadValidationStatus(@Nonnull String id, @Nonnull List<String> messageList,
            @Nonnull UploadStatus status) {
        this.id = id;
        this.status = status;

        // no need to create a safe copy of the message list, since the builder will do that for us
        this.messageList = messageList;
    }

    /**
     * Constructs and validates an UploadValidationStatus from an Upload object.
     *
     * @param upload
     *         Bridge server upload metadata object, must be non-null
     * @return validated UploadValidationStatus object
     * @throws InvalidEntityException
     *         if called upload is null or contains invalid fields
     */
    public static UploadValidationStatus from(@Nonnull Upload upload) throws InvalidEntityException {
        if (upload == null) {
            throw new InvalidEntityException(String.format(Validate.CANNOT_BE_NULL, "upload"));
        }
        return new Builder().withId(upload.getUploadId()).withMessageList(upload.getValidationMessageList())
                .withStatus(upload.getStatus()).build();
    }

    /** Unique upload ID, as generated by the request upload API. */
    public @Nonnull String getId() {
        return id;
    }

    /**
     * Validation message list, frequently used for error messages.
     *
     * @see org.sagebionetworks.bridge.models.upload.Upload#getValidationMessageList
     */
    public @Nonnull List<String> getMessageList() {
        return messageList;
    }

    /** Represents upload status, such as requested, validation in progress, validation failed, or succeeded. */
    public @Nonnull UploadStatus getStatus() {
        return status;
    }

    /** Builder for UploadValidationStatus. */
    public static class Builder {
        private String id;
        private List<String> messageList;
        private UploadStatus status;

        /** @see org.sagebionetworks.bridge.models.upload.UploadValidationStatus#getId */
        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadValidationStatus#getMessageList */
        public Builder withMessageList(List<String> messageList) {
            this.messageList = messageList;
            return this;
        }

        /** @see org.sagebionetworks.bridge.models.upload.UploadValidationStatus#getStatus */
        public Builder withStatus(UploadStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Builds and validates an UploadValidationStatus object. id must be non-null and non-empty. messageList must
         * be non-null and must contain strings that are non-null and non-empty. status must be non-null.
         *
         * @return a validated UploadValidationStatus instance
         * @throws InvalidEntityException
         *         if called with invalid fields
         */
        public UploadValidationStatus build() throws InvalidEntityException {
            // Validate messageList. We need to do this upfront, since ImmutableList will crash if this is invalid.
            // We also can't use Validate.entityThrowingException(), since that only works with BridgeEntities.
            if (messageList == null) {
                throw new InvalidEntityException(String.format(Validate.CANNOT_BE_NULL, "messageList"));
            }
            int numMessages = messageList.size();
            for (int i = 0; i < numMessages; i++) {
                if (Strings.isNullOrEmpty(messageList.get(i))) {
                    throw new InvalidEntityException(String.format(Validate.CANNOT_BE_BLANK,
                            String.format("messageList[%d]", i)));
                }
            }

            // create a safe immutable copy of the message list
            List<String> messageListCopy = ImmutableList.copyOf(messageList);

            UploadValidationStatus validationStatus = new UploadValidationStatus(id, messageListCopy, status);
            Validate.entityThrowingException(UploadValidationStatusValidator.INSTANCE, validationStatus);
            return validationStatus;
        }
    }
}

package org.sagebionetworks.bridge.models.upload;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * This class represents a field definition for an upload schema. This could map to a top-level key-value pair in the
 * raw JSON, or to a column in a Synapse table.
 */
@JsonDeserialize(as = DynamoUploadFieldDefinition.class)
@BridgeTypeName("UploadFieldDefinition")
public interface UploadFieldDefinition extends BridgeEntity {
    /**
     * Used for MULTI_CHOICE. True if the multi-choice field allows an "other" answer with user freeform text. This
     * tells BridgeEX to reserve an "other" column for this field. Can be null, so that the number of field parameters
     * doesn't explode.
     */
    @Nullable Boolean getAllowOtherChoices();

    /**
     * Used for ATTACHMENT_V2 types. Used as a hint by BridgeEX to preserve the file extension as a quality-of-life
     * improvement. Optional, defaults to ".tmp".
     * */
    @Nullable String getFileExtension();

    /**
     * Used for ATTACHMENT_V2 types. Used as a hint by BridgeEX to mark a Synapse file handle with the correct MIME
     * type as a quality-of-life improvement. Optional, defaults to "application/octet-stream".
     */
    @Nullable String getMimeType();

    /**
     * Used for STRING, SINGLE_CHOICE, and INLINE_JSON_BLOB types. This is a hint for BridgeEX to create a Synapse
     * column with the right width.
     */
    @Nullable Integer getMaxLength();

    /**
     * <p>
     * Used for MULTI_CHOICE types. This lists all valid answers for this field. It is used by BridgeEX to create the
     * Synapse table columns for MULTI_CHOICE fields. This is a list because order matters, in terms of Synapse
     * column order. Must be specified if the field type is a MULTI_CHOICE.
     * </p>
     * <p>
     * For schemas generated from surveys, this list will be the "value" in the survey question option, or the "label"
     * if value is not specified.
     * </p>
     */
    @Nullable List<String> getMultiChoiceAnswerList();

    /** The field name. */
    @Nonnull String getName();

    /** True if the field is required to have data, false otherwise. */
    boolean isRequired();

    /**
     * The field's type.
     *
     * @see org.sagebionetworks.bridge.models.upload.UploadFieldType
     */
    @Nonnull UploadFieldType getType();

    /**
     * True if this field is a text-field with unbounded length. (Only applies to fields that are serialized as text,
     * such as INLINE_JSON_BLOB, SINGLE_CHOICE, or STRING. Can be null, so that the number of field parameters doesn't
     * explode.
     */
    @Nullable Boolean isUnboundedText();
}

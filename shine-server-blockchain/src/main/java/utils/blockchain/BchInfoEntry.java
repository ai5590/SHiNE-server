//package utils.blockchain;
//
//import com.fasterxml.jackson.annotation.JsonCreator;
//import com.fasterxml.jackson.annotation.JsonProperty;
//import java.util.Base64;
//
///**
// * BchInfoEntry — данные об одной цепочке блокчейна.
// * Используется менеджером BchInfoManager.
// */
//public final class BchInfoEntry {
//
//    @JsonProperty("blockchainId")
//    public final long blockchainId;
//
//    @JsonProperty("userLogin")
//    public final String userLogin;
//
//    @JsonProperty("publicKeyBase64")
//    public final String publicKeyBase64;
//
//    @JsonProperty("blockchainSizeLimit")
//    public final int blockchainSizeLimit;
//
//    @JsonProperty("blockchainSize")
//    public final int blockchainSize;
//
//    @JsonProperty("lastBlockNumber")
//    public final int lastBlockNumber;
//
//    @JsonProperty("lastBlockHash")
//    public final String lastBlockHash;
//
//    @JsonCreator
//    public BchInfoEntry(
//            @JsonProperty("blockchainId") long blockchainId,
//            @JsonProperty("userLogin") String userLogin,
//            @JsonProperty("publicKeyBase64") String publicKeyBase64,
//            @JsonProperty("blockchainSizeLimit") int blockchainSizeLimit,
//            @JsonProperty("blockchainSize") int blockchainSize,
//            @JsonProperty("lastBlockNumber") int lastBlockNumber,
//            @JsonProperty("lastBlockHash") String lastBlockHash
//    ) {
//        this.blockchainId = blockchainId;
//        this.userLogin = userLogin == null ? "" : userLogin;
//        this.publicKeyBase64 = publicKeyBase64;
//        this.blockchainSizeLimit = blockchainSizeLimit;
//        this.blockchainSize = blockchainSize;
//        this.lastBlockNumber = lastBlockNumber;
//        this.lastBlockHash = lastBlockHash == null ? "" : lastBlockHash;
//    }
//
//    /** Публичный ключ в бинарном виде (32 байта) или null, если битый. */
//    public byte[] getPublicKey32() {
//        try {
//            byte[] raw = Base64.getDecoder().decode(publicKeyBase64);
//            return (raw != null && raw.length == 32) ? raw : null;
//        } catch (IllegalArgumentException e) {
//            return null;
//        }
//    }
//}

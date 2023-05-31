package org.example;

import lombok.*;

import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
public class MerkleProof {
    enum Direction {
        LEFT,
        RIGHT
    }

    @NonNull
    public byte[] hash;
    @NonNull
    public Direction direction;

    @Override
    public String toString() {
        String hash = HexFormat.of().formatHex(this.hash);
        String direction = this.direction.toString();
        return hash.concat("  is ".concat(direction).concat(" Child"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MerkleProof that = (MerkleProof) o;
        return Arrays.equals(hash, that.hash) && direction == that.direction;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(direction);
        result = 31 * result + Arrays.hashCode(hash);
        return result;
    }
}

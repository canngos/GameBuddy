package com.gamebuddy.shared.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One of the stock profile pictures offered to a gamer who does not want to upload one.
 *
 * <p>Nothing here is for sale. The {@code isSpecial} and {@code price} columns are gone
 * along with paid avatars: with nothing purchasable they could each only ever hold one
 * value, and a column that is always false is a trap for whoever reads it next and assumes
 * it means something.
 *
 * <p>No schema on the table name, deliberately. Three services declared
 * {@code schema = "schappl"} while a fourth relied on the default schema, so an avatar
 * bought through one service landed in a table the others never read and the profile
 * screen showed nothing.
 */
@Entity
@Table(name = "avatars")
@Getter
@Setter
@NoArgsConstructor
public class Avatars implements Serializable {

    @Id
    private UUID id;

    /** An object key such as {@code default-avatars/avatar-01.png}; resolved by AvatarUrls. */
    private String image;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Avatars other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}

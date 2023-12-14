package de.taimos.dvalin.jpa;

/*
 * #%L
 * JPA support for dvalin using Hibernate
 * %%
 * Copyright (C) 2015 Taimos GmbH
 * %%
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
 * #L%
 */

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.EnhancedUserType;
import org.hibernate.usertype.UserTypeSupport;
import org.joda.time.DateTime;

import java.io.Serial;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;
import java.util.Objects;

public class JodaDateTimeType implements EnhancedUserType<DateTime>, Serializable {

    /**
     *
     */
    public static final JodaDateTimeType INSTANCE = new JodaDateTimeType();

    @Serial
    private static final long serialVersionUID = -7443774477681244536L;

    private final UserTypeSupport<Date> userTypeSupport = new UserTypeSupport<>(Date.class, this.getSqlType());

    @Override
    public int getSqlType() {
        return Types.TIMESTAMP;
    }

    @Override
    public String toSqlLiteral(DateTime dateTime) {
        return null;
    }

    @Override
    public Class<DateTime> returnedClass() {
        return DateTime.class;
    }

    @Override
    public boolean equals(final DateTime x, final DateTime y) throws HibernateException {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(final DateTime object) throws HibernateException {
        return object.hashCode();
    }

    @Override
    public DateTime nullSafeGet(ResultSet resultSet, int i, SharedSessionContractImplementor sharedSessionContractImplementor, Object o) throws SQLException {

        final Object timestamp = this.userTypeSupport.nullSafeGet(resultSet, i, sharedSessionContractImplementor, o);
        if (timestamp == null) {
            return null;
        }

        return new DateTime(timestamp);
    }

    @Override
    public void nullSafeSet(final PreparedStatement st, final DateTime value, final int index, final SharedSessionContractImplementor session) throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, this.getSqlType());
        } else {
            st.setDate(index, new java.sql.Date(value.toDate().getTime()));
        }
    }

    @Override
    public DateTime deepCopy(final DateTime value) throws HibernateException {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(final DateTime value) throws HibernateException {
        return value;
    }

    @Override
    public DateTime assemble(final Serializable cached, final Object value) throws HibernateException {
        return (DateTime) cached;
    }

    @Override
    public DateTime replace(final DateTime original, final DateTime target, final Object owner) throws HibernateException {
        return original;
    }

    @Override
    public String toString(DateTime value) throws HibernateException {
        return value.toString();
    }

    @Override
    public DateTime fromStringValue(CharSequence charSequence) throws HibernateException {
        return new DateTime(charSequence);
    }
}

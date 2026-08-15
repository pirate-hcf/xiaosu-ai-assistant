package com.xiaosu.mapper.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(UUID.class)
@MappedJdbcTypes({JdbcType.CHAR, JdbcType.VARCHAR})
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, UUID value, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, value.toString());
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return fromString(resultSet.getString(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return fromString(resultSet.getString(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return fromString(statement.getString(columnIndex));
    }

    private static UUID fromString(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}

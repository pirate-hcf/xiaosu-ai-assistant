package com.xiaosu.mapper.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import com.xiaosu.domain.MessageStatus;

@MappedTypes(MessageStatus.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class MessageStatusTypeHandler extends BaseTypeHandler<MessageStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, MessageStatus value, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, value.databaseValue());
    }

    @Override
    public MessageStatus getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return fromString(resultSet.getString(columnName));
    }

    @Override
    public MessageStatus getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return fromString(resultSet.getString(columnIndex));
    }

    @Override
    public MessageStatus getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return fromString(statement.getString(columnIndex));
    }

    private static MessageStatus fromString(String value) {
        return value == null ? null : MessageStatus.fromDatabase(value);
    }
}

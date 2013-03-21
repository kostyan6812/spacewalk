<%@ taglib uri="http://rhn.redhat.com/rhn" prefix="rhn" %>
<%@ taglib uri="http://rhn.redhat.com/tags/list" prefix="rl" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c-rt" %>

<html>
<head>
    <meta name="page-decorator" content="none" />
</head>
<body>
    <rhn:toolbar base="h1" img="/img/rhn-icon-bug-ex.gif">
        <bean:message key="software.crashes.overview"/>
    </rhn:toolbar>

    <div class="page-summary">
        <p><bean:message key="software.crashes.overview.summary"/></p>
    </div>

    <rl:listset name="identicalCrashes">
        <rhn:csrf />
        <rhn:submitted />

        <div class="full-width-wrapper" style="clear: both;">
            <rl:list emptykey="nocrashes.message"
                     alphabarcolumn="uuid">
                <rl:decorator name="PageSizeDecorator"/>
                <rl:column headerkey="crashes.jsp.uuid"
                           bound="false"
                           sortattr="uuid"
                           sortable="true">
                    ${current.uuid}
                </rl:column>
                <rl:column headerkey="crashes.jsp.totalcrashcount"
                           bound="false"
                           sortattr="totalCrashCount"
                           sortable="true">
                    ${current.totalCrashCount}
                </rl:column>
                <rl:column headerkey="crashes.jsp.affectedsystemscount"
                           bound="false"
                           sortattr="systemCount"
                           sortable="true">
                    ${current.systemCount}
                </rl:column>
                <rl:column headerkey="crashes.jsp.lastoccurence"
                           bound="false"
                           sortattr="lastReport"
                           sortable="true">
                    ${current.lastReport}
                </rl:column>
            </rl:list>
        </div>
        <rl:csv name="identicalCrashes"
                exportColumns="uuid,totalCrashCount,systemCount,lastReport" />
    </rl:listset>
</body>
</html>

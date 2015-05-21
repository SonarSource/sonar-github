/*
 * SonarQube :: GitHub Plugin
 * Copyright (C) 2015 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.kohsuke.github;

import java.io.IOException;
import java.net.URL;
import java.util.Date;

/**
 * Comment to the issue
 *
 * @author Kohsuke Kawaguchi
 */
public class GHIssueComment extends GHObject {
    GHIssue owner;

    private String body, gravatar_id;
    private GHUser user;

    /*package*/ GHIssueComment wrapUp(GHIssue owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Gets the issue to which this comment is associated.
     */
    public GHIssue getParent() {
        return owner;
    }

    /**
     * The comment itself.
     */
    public String getBody() {
        return body;
    }

    /**
     * Gets the ID of the user who posted this comment.
     */
    @Deprecated
    public String getUserName() {
        return user.getLogin();
    }

    /**
     * Gets the user who posted this comment.
     */
    public GHUser getUser() throws IOException {
        return owner.root.getUser(user.getLogin());
    }
    
    /**
     * @deprecated This object has no HTML URL.
     */
    @Override
    public URL getHtmlUrl() {
        return null;
    }
    
    /**
     * Updates the body of the issue comment.
     */
    public void update(String body) throws IOException {
        GHIssueComment r = new Requester(owner.root)
                .with("body", body)
                .method("PATCH").to(getApiTail(), GHIssueComment.class);
        this.body = body;
    }

    /**
     * Deletes this issue comment.
     */
    public void delete() throws IOException {
        new Requester(owner.root).method("DELETE").to(getApiTail());
    }
    
    private String getApiTail() {
        return owner.getIssuesApiRoute() + "/comments/" + id;
    }
}

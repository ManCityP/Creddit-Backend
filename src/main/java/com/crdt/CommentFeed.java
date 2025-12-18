package com.crdt;

import java.util.ArrayList;
import java.util.Map;

public record CommentFeed(
        ArrayList<Comment> parents,
        Map<Integer, ArrayList<Comment>> lv2,
        Map<Integer, ArrayList<Comment>> lv3,
        Map<Integer, Integer> votes
) {}

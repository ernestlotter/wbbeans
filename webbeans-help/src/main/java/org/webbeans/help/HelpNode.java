package org.webbeans.help;

import java.util.List;

import org.openide.util.lookup.Lookups;
import org.webbeans.nodes.AbstractNode;
import org.webbeans.nodes.Children;
import org.webbeans.nodes.Node;

/** A topic of the table of contents; the {@link HelpSet.Topic} is in the lookup. */
final class HelpNode extends AbstractNode {

    private final HelpSet.Topic topic;

    HelpNode(HelpSet.Topic topic, boolean flat) {
        super(flat || topic.getChildren().isEmpty() ? Children.LEAF : new TopicChildren(topic.getChildren()), Lookups.singleton(topic));
        this.topic = topic;
        setName(topic.getId());
        setDisplayName(topic.getTitle());
        setIconBase("org/webbeans/help/" + (topic.getChildren().isEmpty() ? "page.svg" : "book.svg"));
    }

    HelpSet.Topic getTopic() {
        return topic;
    }

    /** The children of a topic, or a flat list (search results). */
    static final class TopicChildren extends Children.Keys<HelpSet.Topic> {
        private final boolean flat;

        TopicChildren(List<HelpSet.Topic> topics) {
            this(topics, false);
        }

        TopicChildren(List<HelpSet.Topic> topics, boolean flat) {
            this.flat = flat;
            setKeys(topics);
        }

        @Override
        protected Node[] createNodes(HelpSet.Topic key) {
            return new Node[] {new HelpNode(key, flat)};
        }
    }

    /** Finds the node of a topic in a tree. */
    static Node find(Node root, HelpSet.Topic topic) {
        if (root instanceof HelpNode && ((HelpNode) root).topic == topic) {
            return root;
        }
        for (Node n : root.getChildren().getNodes()) {
            Node f = find(n, topic);
            if (f != null) {
                return f;
            }
        }
        return null;
    }
}

import { ButtonLink } from "../components/ui/Button";
import { EmptyState } from "../components/ui/Feedback";

export default function NotFound() {
  return (
    <EmptyState
      title="Wrong turn."
      message="This page isn't on the route. Let's get you back on course."
      action={<ButtonLink to="/">Back to Dashboard</ButtonLink>}
    />
  );
}

import { useT } from '../i18n/useT';
import { openPrivacy, openTerms } from '../legal';
import { Text } from '../ui';

/**
 * The age-and-terms sentence, wherever an account is about to be made.
 *
 * It arrives as ordered segments so each language keeps its own word order around the bold
 * age phrase and the two document links. One rendering for the registration form and the
 * social consent step: two wordings of the same promise would be one of them wrong.
 */
export function ConsentText() {
  const t = useT();
  return (
    <Text variant="body">
      {t.auth.register.consent.map((segment, index) =>
        segment.link ? (
          <Text
            key={index}
            variant="bodyStrong"
            className="text-primary"
            onPress={segment.link === 'terms' ? openTerms : openPrivacy}
          >
            {segment.text}
          </Text>
        ) : segment.bold ? (
          <Text key={index} variant="bodyStrong">
            {segment.text}
          </Text>
        ) : (
          segment.text
        ),
      )}
    </Text>
  );
}

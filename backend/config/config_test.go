package config

import "testing"

func TestParseVerificationToken(t *testing.T) {
	cases := map[string]string{
		"5Lp9D5qEGGO3iJJYtN4NCPrwU9sXCbDO_mblvItUahY":                                                      "5Lp9D5qEGGO3iJJYtN4NCPrwU9sXCbDO_mblvItUahY",
		` <meta name="google-site-verification" content="5Lp9D5qEGGO3iJJYtN4NCPrwU9sXCbDO_mblvItUahY" /> `: "5Lp9D5qEGGO3iJJYtN4NCPrwU9sXCbDO_mblvItUahY",
		`"><script>alert(1)</script>`:                                                                      "",
		"":                                                                                                 "",
	}
	for in, want := range cases {
		if got := parseVerificationToken(in); got != want {
			t.Errorf("parseVerificationToken(%q) = %q, want %q", in, got, want)
		}
	}
}

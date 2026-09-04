export type TimeFilter =
  | '24_hour'
  | '1_week'
  | '1_month'
  | 'any_time';

export class LinkedInSearchParams {
  readonly timeFilter: TimeFilter;
  readonly location: string;
  readonly remote: boolean;
  readonly easyApply: boolean = true;

  constructor(
    timeFilter: TimeFilter,
    location: string,
    remote: boolean,
  ) {
    this.timeFilter = timeFilter;
    this.location = location;
    this.remote = remote;
  }
}
import { Reader } from "./base/Reader";
import { GeneralContentReader } from "./impl/GeneralContentReader";
import { LinkedinReader } from "./impl/LinkedinReader";

export class ReaderFactory {

    private static readonly knownReaders: Record<string, Reader> = {
        "linkedin.com": new LinkedinReader(),
    };

    static getReader(hostname: string): Reader {

        const normalizedHost = hostname
            .replace(/^www\./, "")
            .toLowerCase();

        return (
            this.knownReaders[normalizedHost]
            ?? new GeneralContentReader()
        );

    }

}
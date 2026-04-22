import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ClaimStep1Component } from './claim-step1.component';

describe('ClaimStep1Component', () => {
  let component: ClaimStep1Component;
  let fixture: ComponentFixture<ClaimStep1Component>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ClaimStep1Component]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ClaimStep1Component);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
